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
    $status = $input['status'] ?? '';
    $doneDate = $input['doneDate'] ?? date('Y-m-d');
    $notes = $input['notes'] ?? '';
    $photo1 = $input['photo1'] ?? '';
    $photo2 = $input['photo2'] ?? '';
    $photo3 = $input['photo3'] ?? '';

    if (empty($mntId)) {
        throw new Exception('mntId is required');
    }

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
    $propID = $row_info['propID'];
    $invNo = $row_info['invNo'];
    $uniqID = $row_info['uniqID'];

    // Get current note from tblevent to check if it's the same
    $sql_get_note = "SELECT note FROM tblevent WHERE id = '$mntId'";
    $result_get_note = mysqli_query($conn, $sql_get_note);
    $current_note = '';
    if ($result_get_note && $row_note = mysqli_fetch_assoc($result_get_note)) {
        $current_note = $row_note['note'] ?? '';
    }

    // Prepare note update - only update if note is different
    $notes_escaped = mysqli_real_escape_string($conn, $notes);
    $note_update = '';

    if (empty($current_note)) {
        // If current note is empty, set new note
        $note_update = "note = '$notes_escaped'";
    } elseif ($current_note !== $notes) {
        // If note is different, update it
        $note_update = "note = '$notes_escaped'";
    } else {
        // If note is the same, don't update note field
        $note_update = "note = note"; // Keep existing note
    }

    // Update tblevent menggunakan mysqli (fungsi asli endpoint)
    // IMPORTANT: Only update status if it's provided and not empty
    // Status should only be "done" when ALL tasks are completed, not when updating notes/photos
    $status_update = '';
    if (!empty($status)) {
        $status_escaped = mysqli_real_escape_string($conn, $status);
        $status_update = "status = '$status_escaped',";
    }

    $done_date_update = '';
    if (!empty($doneDate)) {
        $done_date_escaped = mysqli_real_escape_string($conn, $doneDate);
        $done_date_update = "done_date = '$done_date_escaped',";
    }

    // Build SQL query - only include fields that need to be updated
    $update_fields = [];
    if (!empty($status_update)) {
        $update_fields[] = trim($status_update, ',');
    }
    if (!empty($done_date_update)) {
        $update_fields[] = trim($done_date_update, ',');
    }
    if (!empty($note_update)) {
        $update_fields[] = $note_update;
    }

    if (empty($update_fields)) {
        throw new Exception('No fields to update');
    }

    $sql = "UPDATE tblevent SET " . implode(", ", $update_fields) . " WHERE id = '$mntId'";

    $result = mysqli_query($conn, $sql);

    if ($result) {
        $affected_rows = mysqli_affected_rows($conn);

        // Update tblmnthistory dengan photo1, photo2, photo3 (jika ada)
        if (!empty($photo1) || !empty($photo2) || !empty($photo3)) {
            // Cek apakah record sudah ada di tblmnthistory
            $check_history = "SELECT no FROM tblmnthistory WHERE mntId = '$invNo' AND mntUniq = '$uniqID' AND propID = '$propID' ORDER BY date DESC LIMIT 1";
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

        echo json_encode([
            'success' => true,
            'message' => 'Event updated successfully',
            'affected_rows' => $affected_rows
        ]);
    } else {
        throw new Exception('Failed to update event: ' . mysqli_error($conn));
    }
} catch (Exception $e) {
    http_response_code(400);
    echo json_encode([
        'success' => false,
        'error' => $e->getMessage()
    ]);
}

mysqli_close($conn);
