<?php
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

// Handle preflight requests
if ($_SERVER['REQUEST_METHOD'] == 'OPTIONS') {
    exit(0);
}

// Include koneksi database
include 'koneksi.php';

try {
    // Get parameters from query string
    $mntId = isset($_GET['mntId']) ? $_GET['mntId'] : '';
    $propID = isset($_GET['propID']) ? $_GET['propID'] : '';

    if (empty($mntId) || empty($propID)) {
        throw new Exception('Maintenance ID and Property ID are required');
    }

    // Debug: Log parameters untuk troubleshooting
    error_log("Maintenance History Parameters - mntId: $mntId, propID: $propID");

    // Sanitize inputs
    $mntId = mysqli_real_escape_string($conn, $mntId);
    $propID = mysqli_real_escape_string($conn, $propID);

    // Debug: Log parameters untuk troubleshooting
    error_log("Maintenance History Parameters - mntId: $mntId, propID: $propID");

    // Debug: Count total records before LIMIT
    $countQuery = "SELECT COUNT(*) as total FROM tblmnthistory WHERE mntId = '$mntId' AND propID = '$propID'";
    $countResult = mysqli_query($conn, $countQuery);
    $countData = mysqli_fetch_assoc($countResult);
    error_log("Total records in tblmnthistory: " . $countData['total']);

    // Query dengan JOIN yang benar berdasarkan diagram korelasi:
    // Menggunakan kondisi tanggal dari tblmnthistory.date
    // Include photo1, photo2, photo3 dari tblmnthistory
    $query = "
        SELECT 
            mh.date,
            mh.jobtask,
            mh.doneby,
            mh.remark,
            mh.photo1,
            mh.photo2,
            mh.photo3,
            COALESCE(te.note, '') as notes
        FROM tblmnthistory mh
        LEFT JOIN tblevent te ON mh.mntId = te.invNo 
            AND mh.propID = te.propID 
            AND mh.date = te.done_date
        WHERE mh.mntId = '$mntId' 
        AND mh.propID = '$propID'
    ";

    // Debug: Log query untuk troubleshooting
    error_log("Maintenance History Query: " . $query);

    $result = mysqli_query($conn, $query);

    if (!$result) {
        throw new Exception('Database query failed: ' . mysqli_error($conn));
    }

    $historyData = [];
    while ($row = mysqli_fetch_assoc($result)) {
        $historyData[] = [
            'date' => $row['date'],
            'jobtask' => $row['jobtask'],
            'doneby' => $row['doneby'],
            'remark' => $row['remark'],
            'photo1' => $row['photo1'] ?? '',
            'photo2' => $row['photo2'] ?? '',
            'photo3' => $row['photo3'] ?? '',
            'notes' => $row['notes'] ?? ''
        ];
    }

    // Debug: Log result count
    error_log("Maintenance History Result Count: " . count($historyData));

    // Debug: Log first record to verify data
    if (!empty($historyData)) {
        error_log("First record data: " . json_encode($historyData[0]));
    }

    echo json_encode([
        'success' => true,
        'data' => $historyData,
        'message' => 'Maintenance history retrieved successfully'
    ]);
} catch (Exception $e) {
    error_log("Maintenance History Error: " . $e->getMessage());
    http_response_code(400);
    echo json_encode([
        'success' => false,
        'error' => $e->getMessage()
    ]);
}

mysqli_close($conn);
