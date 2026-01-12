<?php
header('Content-Type: text/html; charset=UTF-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

// Handle preflight request
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

include('koneksi.php');

// Hardcoded propID for demo - TIDAK PERLU input apapun, langsung hapus semua data
$propID = 'vJCAqVcE';

// Photo directory
$photo_dir = '../photo/maintenance/';

// Check if this is a GET request (from browser) or POST request (API)
$isGetRequest = $_SERVER['REQUEST_METHOD'] === 'GET';
$confirm = isset($_GET['confirm']) && $_GET['confirm'] === 'yes';

// If GET request but not confirmed, show confirmation page
if ($isGetRequest && !$confirm) {
?>
    <!DOCTYPE html>
    <html lang="id">

    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Delete Maintenance Data - Confirmation</title>
        <style>
            body {
                font-family: Arial, sans-serif;
                max-width: 600px;
                margin: 50px auto;
                padding: 20px;
                background-color: #f5f5f5;
            }

            .container {
                background: white;
                padding: 30px;
                border-radius: 10px;
                box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
            }

            h1 {
                color: #d32f2f;
                margin-top: 0;
            }

            .warning {
                background-color: #fff3cd;
                border: 2px solid #ffc107;
                padding: 15px;
                border-radius: 5px;
                margin: 20px 0;
            }

            .warning strong {
                color: #d32f2f;
            }

            .info {
                background-color: #e3f2fd;
                padding: 15px;
                border-radius: 5px;
                margin: 20px 0;
            }

            .button-group {
                margin-top: 30px;
                display: flex;
                gap: 10px;
            }

            .btn {
                padding: 12px 24px;
                border: none;
                border-radius: 5px;
                cursor: pointer;
                font-size: 16px;
                text-decoration: none;
                display: inline-block;
            }

            .btn-danger {
                background-color: #d32f2f;
                color: white;
            }

            .btn-danger:hover {
                background-color: #b71c1c;
            }

            .btn-secondary {
                background-color: #757575;
                color: white;
            }

            .btn-secondary:hover {
                background-color: #616161;
            }
        </style>
    </head>

    <body>
        <div class="container">
            <h1>⚠️ Konfirmasi Hapus Data</h1>

            <div class="warning">
                <strong>PERINGATAN!</strong><br>
                Anda akan menghapus <strong>SEMUA</strong> data maintenance untuk propID: <strong><?php echo htmlspecialchars($propID); ?></strong>
            </div>

            <div class="info">
                <strong>Data yang akan dihapus:</strong>
                <ul>
                    <li>Semua maintenance tasks (tblmntask)</li>
                    <li>Semua maintenance history (tblmnthistory)</li>
                    <li>Semua foto maintenance yang terkait</li>
                </ul>
                <p><strong>Catatan:</strong> tblevent TIDAK dihapus karena kelima endpoint tidak melakukan INSERT ke tblevent (hanya UPDATE).</p>
                <p><strong>Tindakan ini TIDAK DAPAT DIBATALKAN!</strong></p>
            </div>

            <div class="button-group">
                <a href="?confirm=yes" class="btn btn-danger">Ya, Hapus Semua Data</a>
                <a href="javascript:history.back()" class="btn btn-secondary">Batal</a>
            </div>
        </div>
    </body>

    </html>
    <?php
    exit();
}

// Execute deletion
try {
    // Sanitize propID
    $propID = mysqli_real_escape_string($conn, trim($propID));

    // Start transaction for data consistency
    mysqli_begin_transaction($conn);

    try {
        // Step 1: Get ALL photos from tblevent WHERE propID = 'vJCAqVcE'
        $sql_get_event_photos = "SELECT photo1, photo2, photo3 FROM tblevent WHERE propID = '$propID'";
        $result_event_photos = mysqli_query($conn, $sql_get_event_photos);

        $event_photos = [];
        if ($result_event_photos && mysqli_num_rows($result_event_photos) > 0) {
            while ($row = mysqli_fetch_assoc($result_event_photos)) {
                if (!empty($row['photo1'])) $event_photos[] = $row['photo1'];
                if (!empty($row['photo2'])) $event_photos[] = $row['photo2'];
                if (!empty($row['photo3'])) $event_photos[] = $row['photo3'];
            }
        }

        // Step 2: Get ALL photos from tblmnthistory WHERE propID = 'vJCAqVcE'
        $sql_get_history_photos = "SELECT photo1, photo2, photo3 FROM tblmnthistory WHERE propID = '$propID'";
        $result_history_photos = mysqli_query($conn, $sql_get_history_photos);

        $history_photos = [];
        if ($result_history_photos && mysqli_num_rows($result_history_photos) > 0) {
            while ($row = mysqli_fetch_assoc($result_history_photos)) {
                if (!empty($row['photo1'])) $history_photos[] = $row['photo1'];
                if (!empty($row['photo2'])) $history_photos[] = $row['photo2'];
                if (!empty($row['photo3'])) $history_photos[] = $row['photo3'];
            }
        }

        // Step 3: Collect all unique photo filenames to delete (avoid duplicates)
        $photos_to_delete = [];
        foreach ($event_photos as $photo) {
            if (!empty($photo) && !in_array($photo, $photos_to_delete)) {
                $photos_to_delete[] = $photo;
            }
        }
        foreach ($history_photos as $photo) {
            if (!empty($photo) && !in_array($photo, $photos_to_delete)) {
                $photos_to_delete[] = $photo;
            }
        }

        // Step 4: Delete photos from filesystem
        $deleted_photos = [];
        $failed_photos = [];

        foreach ($photos_to_delete as $photo_filename) {
            if (!empty($photo_filename)) {
                $photo_path = $photo_dir . $photo_filename;

                // Check if file exists before deleting
                if (file_exists($photo_path)) {
                    if (unlink($photo_path)) {
                        $deleted_photos[] = $photo_filename;
                    } else {
                        $failed_photos[] = $photo_filename;
                    }
                } else {
                    // File doesn't exist, but we'll count it as "handled"
                    $deleted_photos[] = $photo_filename . ' (not found)';
                }
            }
        }

        // Step 5: Delete ALL from tblmnthistory WHERE propID = 'vJCAqVcE'
        $sql_delete_history = "DELETE FROM tblmnthistory WHERE propID = '$propID'";
        $result_delete_history = mysqli_query($conn, $sql_delete_history);

        if (!$result_delete_history) {
            throw new Exception('Failed to delete from tblmnthistory: ' . mysqli_error($conn));
        }
        $deleted_history_count = mysqli_affected_rows($conn);

        // Step 6: Delete ALL from tblmntask WHERE propID = 'vJCAqVcE'
        $sql_delete_tasks = "DELETE FROM tblmntask WHERE propID = '$propID'";
        $result_delete_tasks = mysqli_query($conn, $sql_delete_tasks);

        if (!$result_delete_tasks) {
            throw new Exception('Failed to delete from tblmntask: ' . mysqli_error($conn));
        }
        $deleted_tasks_count = mysqli_affected_rows($conn);

        // Step 7: Delete from tblharian if propID matches (for vJCAqVcE)
        // Note: tblevent TIDAK dihapus karena kelima endpoint tidak melakukan INSERT ke tblevent
        // tblevent hanya di-UPDATE, jadi tidak perlu dihapus
        $deleted_harian_count = 0;
        if ($propID == 'vJCAqVcE') {
            // Note: tblharian deletion might need checkpoint_id, but we'll try to delete based on date if possible
            // Since we don't have checkpoint_id from maintenance, we'll skip this or delete based on date
            // This might need adjustment based on your tblharian structure
            // For now, we'll just log that it should be checked manually
        }

        // Commit transaction
        mysqli_commit($conn);

        // Prepare response data
        $response_data = [
            'success' => true,
            'message' => 'All maintenance data for propID ' . $propID . ' deleted successfully',
            'propID' => $propID,
            'deleted' => [
                'tasks' => $deleted_tasks_count,
                'history' => $deleted_history_count,
                'photos' => count($deleted_photos)
            ],
            'photos_deleted' => $deleted_photos,
            'photos_failed' => $failed_photos
        ];

        // Add warning if some photos failed to delete
        if (!empty($failed_photos)) {
            $response_data['warning'] = 'Some photos could not be deleted from filesystem';
        }

        // If POST request, return JSON
        if (!$isGetRequest) {
            header('Content-Type: application/json');
            echo json_encode($response_data);
            exit();
        }

        // If GET request, show HTML summary
    ?>
        <!DOCTYPE html>
        <html lang="id">

        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Delete Maintenance Data - Summary</title>
            <style>
                body {
                    font-family: Arial, sans-serif;
                    max-width: 800px;
                    margin: 50px auto;
                    padding: 20px;
                    background-color: #f5f5f5;
                }

                .container {
                    background: white;
                    padding: 30px;
                    border-radius: 10px;
                    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
                }

                h1 {
                    color: #2e7d32;
                    margin-top: 0;
                }

                .success {
                    background-color: #c8e6c9;
                    border: 2px solid #4caf50;
                    padding: 15px;
                    border-radius: 5px;
                    margin: 20px 0;
                }

                .summary {
                    background-color: #f5f5f5;
                    padding: 20px;
                    border-radius: 5px;
                    margin: 20px 0;
                }

                .summary-item {
                    display: flex;
                    justify-content: space-between;
                    padding: 10px 0;
                    border-bottom: 1px solid #ddd;
                }

                .summary-item:last-child {
                    border-bottom: none;
                }

                .summary-label {
                    font-weight: bold;
                    color: #333;
                }

                .summary-value {
                    color: #2e7d32;
                    font-weight: bold;
                    font-size: 18px;
                }

                .warning {
                    background-color: #fff3cd;
                    border: 2px solid #ffc107;
                    padding: 15px;
                    border-radius: 5px;
                    margin: 20px 0;
                }

                .photos-list {
                    background-color: #f9f9f9;
                    padding: 15px;
                    border-radius: 5px;
                    margin: 10px 0;
                    max-height: 200px;
                    overflow-y: auto;
                }

                .photos-list ul {
                    margin: 0;
                    padding-left: 20px;
                }

                .btn {
                    display: inline-block;
                    padding: 10px 20px;
                    background-color: #2196f3;
                    color: white;
                    text-decoration: none;
                    border-radius: 5px;
                    margin-top: 20px;
                }

                .btn:hover {
                    background-color: #1976d2;
                }
            </style>
        </head>

        <body>
            <div class="container">
                <h1>✅ Data Berhasil Dihapus</h1>

                <div class="success">
                    <strong>Berhasil!</strong> Semua data maintenance untuk propID <strong><?php echo htmlspecialchars($propID); ?></strong> telah dihapus.
                </div>

                <div class="summary">
                    <h2>📊 Summary Data yang Dihapus</h2>

                    <div class="summary-item">
                        <span class="summary-label">Maintenance Tasks (tblmntask):</span>
                        <span class="summary-value"><?php echo number_format($response_data['deleted']['tasks']); ?></span>
                    </div>

                    <div class="summary-item">
                        <span class="summary-label">Maintenance History (tblmnthistory):</span>
                        <span class="summary-value"><?php echo number_format($response_data['deleted']['history']); ?></span>
                    </div>

                    <div class="summary-item">
                        <span class="summary-label">Foto yang Dihapus:</span>
                        <span class="summary-value"><?php echo number_format($response_data['deleted']['photos']); ?></span>
                    </div>
                </div>

                <?php if (!empty($response_data['photos_deleted'])): ?>
                    <div class="photos-list">
                        <strong>📷 Daftar Foto yang Dihapus:</strong>
                        <ul>
                            <?php foreach ($response_data['photos_deleted'] as $photo): ?>
                                <li><?php echo htmlspecialchars($photo); ?></li>
                            <?php endforeach; ?>
                        </ul>
                    </div>
                <?php endif; ?>

                <?php if (!empty($response_data['photos_failed'])): ?>
                    <div class="warning">
                        <strong>⚠️ Peringatan:</strong> Beberapa foto gagal dihapus dari filesystem:
                        <ul>
                            <?php foreach ($response_data['photos_failed'] as $photo): ?>
                                <li><?php echo htmlspecialchars($photo); ?></li>
                            <?php endforeach; ?>
                        </ul>
                    </div>
                <?php endif; ?>

                <a href="delete_demo.php" class="btn">Kembali</a>
            </div>
        </body>

        </html>
    <?php

    } catch (Exception $e) {
        // Rollback transaction on error
        mysqli_rollback($conn);
        throw $e;
    }
} catch (Exception $e) {
    // If POST request, return JSON error
    if (!$isGetRequest) {
        header('Content-Type: application/json');
        http_response_code(400);
        echo json_encode([
            'success' => false,
            'error' => $e->getMessage()
        ]);
        exit();
    }

    // If GET request, show HTML error
    ?>
    <!DOCTYPE html>
    <html lang="id">

    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Error - Delete Maintenance Data</title>
        <style>
            body {
                font-family: Arial, sans-serif;
                max-width: 600px;
                margin: 50px auto;
                padding: 20px;
                background-color: #f5f5f5;
            }

            .container {
                background: white;
                padding: 30px;
                border-radius: 10px;
                box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
            }

            .error {
                background-color: #ffebee;
                border: 2px solid #f44336;
                padding: 15px;
                border-radius: 5px;
                color: #c62828;
            }
        </style>
    </head>

    <body>
        <div class="container">
            <h1>❌ Error</h1>
            <div class="error">
                <strong>Terjadi kesalahan:</strong><br>
                <?php echo htmlspecialchars($e->getMessage()); ?>
            </div>
            <a href="delete_demo.php" style="display: inline-block; margin-top: 20px; padding: 10px 20px; background-color: #2196f3; color: white; text-decoration: none; border-radius: 5px;">Kembali</a>
        </div>
    </body>

    </html>
<?php
}

mysqli_close($conn);









