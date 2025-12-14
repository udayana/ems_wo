<?php
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST');
header('Access-Control-Allow-Headers: Content-Type');

include('koneksi.php');

try {
    $method = $_SERVER['REQUEST_METHOD'];

    if ($method === 'GET') {
        // GET - Ambil notes
        $mntId = $_GET['mntId'] ?? '';
        $propID = $_GET['propID'] ?? '';

        if (empty($mntId)) {
            throw new Exception('mntId is required');
        }

        // Debug print untuk memastikan data yang diterima benar
        error_log("🔍 DEBUG NOTES GET: mntId = $mntId");
        error_log("🔍 DEBUG NOTES GET: propID = $propID");

        // Get notes dari tblevent
        $sql = "SELECT note FROM tblevent WHERE id = '$mntId'";

        if (!empty($propID)) {
            $sql .= " AND propID = '$propID'";
        }

        // Debug print untuk query SQL
        error_log("🔍 DEBUG NOTES GET: SQL Query = $sql");

        $result = mysqli_query($conn, $sql);

        if ($result) {
            $row = mysqli_fetch_assoc($result);
            $notes = $row['note'] ?? '';

            // Debug print untuk hasil query
            error_log("🔍 DEBUG NOTES GET: Query successful, notes = $notes");

            echo json_encode([
                'success' => true,
                'message' => 'Maintenance notes retrieved successfully',
                'notes' => $notes,
                'mntId' => $mntId
            ]);
        } else {
            $error = mysqli_error($conn);
            error_log("🔍 DEBUG NOTES GET: Query failed - $error");
            throw new Exception('Failed to get maintenance notes: ' . $error);
        }
    } elseif ($method === 'POST') {
        // POST - Simpan notes
        $input = json_decode(file_get_contents('php://input'), true);

        if (!$input) {
            throw new Exception('Invalid JSON input');
        }

        $mntId = $input['mntId'] ?? '';
        $notes = $input['notes'] ?? '';
        $propID = $input['propID'] ?? '';
        $photo1 = $input['photo1'] ?? '';
        $photo2 = $input['photo2'] ?? '';
        $photo3 = $input['photo3'] ?? '';

        if (empty($mntId)) {
            throw new Exception('mntId is required');
        }

        // Debug print untuk memastikan data yang diterima benar
        error_log("🔍 DEBUG NOTES POST: mntId = $mntId");
        error_log("🔍 DEBUG NOTES POST: notes = $notes");
        error_log("🔍 DEBUG NOTES POST: propID = $propID");
        error_log("🔍 DEBUG NOTES POST: photo1 = $photo1");
        error_log("🔍 DEBUG NOTES POST: photo2 = $photo2");
        error_log("🔍 DEBUG NOTES POST: photo3 = $photo3");

        // Get propID, invNo, uniqID from tblevent (needed for tblmnthistory update)
        $sql_get_info = "SELECT propID, invNo, uniqID FROM tblevent WHERE id = '$mntId'";
        $result_info = mysqli_query($conn, $sql_get_info);
        if (!$result_info) {
            throw new Exception('Failed to get event info: ' . mysqli_error($conn));
        }
        $row_info = mysqli_fetch_assoc($result_info);
        if (!$row_info) {
            throw new Exception('Event not found');
        }
        $event_propID = $row_info['propID'];
        $invNo = $row_info['invNo'];
        $uniqID = $row_info['uniqID'];

        // Update notes di tblevent - GANTI notes (bukan append)
        // IMPORTANT: Do NOT update status here - only update notes
        $notes_escaped = mysqli_real_escape_string($conn, $notes);
        $sql = "UPDATE tblevent SET 
                note = '$notes_escaped'
                WHERE id = '$mntId'";

        if (!empty($propID)) {
            $sql .= " AND propID = '$propID'";
        }

        // Debug print untuk query SQL
        error_log("🔍 DEBUG NOTES POST: SQL Query = $sql");

        $result = mysqli_query($conn, $sql);

        if ($result) {
            $affected_rows = mysqli_affected_rows($conn);

            // Update tblmnthistory dengan photo1, photo2, photo3 (jika ada)
            // IMPORTANT: This endpoint does NOT change status, only updates notes and photos
            if (!empty($photo1) || !empty($photo2) || !empty($photo3)) {
                // Cek apakah record sudah ada di tblmnthistory
                $check_history = "SELECT no FROM tblmnthistory WHERE mntId = '$invNo' AND mntUniq = '$uniqID' AND propID = '$event_propID' ORDER BY date DESC LIMIT 1";
                $result_check = mysqli_query($conn, $check_history);

                if (mysqli_num_rows($result_check) > 0) {
                    // Get the record no (primary key) to update
                    $history_row = mysqli_fetch_assoc($result_check);
                    $history_no = $history_row['no'];

                    // Update existing record - only update photo fields if they are provided
                    $update_fields = [];
                    if (!empty($photo1)) {
                        $photo1_escaped = mysqli_real_escape_string($conn, $photo1);
                        $update_fields[] = "photo1 = '$photo1_escaped'";
                    }
                    if (!empty($photo2)) {
                        $photo2_escaped = mysqli_real_escape_string($conn, $photo2);
                        $update_fields[] = "photo2 = '$photo2_escaped'";
                    }
                    if (!empty($photo3)) {
                        $photo3_escaped = mysqli_real_escape_string($conn, $photo3);
                        $update_fields[] = "photo3 = '$photo3_escaped'";
                    }

                    if (!empty($update_fields)) {
                        // Update record menggunakan primary key (no)
                        $update_photo = "UPDATE tblmnthistory SET " . implode(", ", $update_fields) .
                            " WHERE no = '$history_no'";
                        mysqli_query($conn, $update_photo);
                    }
                }
            }

            // Debug print untuk hasil query
            error_log("🔍 DEBUG NOTES POST: Query successful, affected rows = $affected_rows");

            echo json_encode([
                'success' => true,
                'message' => 'Maintenance notes updated successfully',
                'affected_rows' => $affected_rows,
                'mntId' => $mntId,
                'notes' => $notes
            ]);
        } else {
            $error = mysqli_error($conn);
            error_log("🔍 DEBUG NOTES POST: Query failed - $error");
            throw new Exception('Failed to update maintenance notes: ' . $error);
        }
    } else {
        throw new Exception('Method not allowed');
    }
} catch (Exception $e) {
    http_response_code(400);
    echo json_encode([
        'success' => false,
        'error' => $e->getMessage()
    ]);
}

mysqli_close($conn);
