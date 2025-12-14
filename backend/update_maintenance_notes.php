<?php
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST');
header('Access-Control-Allow-Headers: Content-Type');

include('koneksi.php');

try {
    $input = json_decode(file_get_contents('php://input'), true);

    if (!$input) {
        throw new Exception('Invalid JSON input');
    }

    $mntId = $input['mntId'] ?? '';
    $notes = $input['notes'] ?? '';
    $propID = $input['propID'] ?? '';

    if (empty($mntId)) {
        throw new Exception('mntId is required');
    }

    // Debug print untuk memastikan data yang diterima benar
    error_log("🔍 DEBUG NOTES: mntId = $mntId");
    error_log("🔍 DEBUG NOTES: notes = $notes");
    error_log("🔍 DEBUG NOTES: propID = $propID");

    // Update notes di tblevent - append notes baru ke notes yang sudah ada
    $sql = "UPDATE tblevent SET 
            note = CASE 
                WHEN note IS NULL OR note = '' THEN '$notes'
                ELSE CONCAT(note, '. ', '$notes')
            END
            WHERE id = '$mntId'";

    if (!empty($propID)) {
        $sql .= " AND propID = '$propID'";
    }

    // Debug print untuk query SQL
    error_log("🔍 DEBUG NOTES: SQL Query = $sql");

    $result = mysqli_query($conn, $sql);

    if ($result) {
        $affected_rows = mysqli_affected_rows($conn);

        // Debug print untuk hasil query
        error_log("🔍 DEBUG NOTES: Query successful, affected rows = $affected_rows");

        echo json_encode([
            'success' => true,
            'message' => 'Maintenance notes updated successfully',
            'affected_rows' => $affected_rows,
            'mntId' => $mntId,
            'notes' => $notes
        ]);
    } else {
        $error = mysqli_error($conn);
        error_log("🔍 DEBUG NOTES: Query failed - $error");
        throw new Exception('Failed to update maintenance notes: ' . $error);
    }
} catch (Exception $e) {
    http_response_code(400);
    echo json_encode([
        'success' => false,
        'error' => $e->getMessage()
    ]);
}

mysqli_close($conn);
