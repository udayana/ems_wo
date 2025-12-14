<?php
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

// Handle preflight requests
if ($_SERVER['REQUEST_METHOD'] == 'OPTIONS') {
    exit(0);
}

include 'koneksi.php';

// Get parameters
$noAssets = isset($_GET['noAssets']) ? $_GET['noAssets'] : '';
$propID = isset($_GET['propID']) ? $_GET['propID'] : '';
$eventId = isset($_GET['eventId']) ? $_GET['eventId'] : '';

// Sanitize inputs
$noAssets = mysqli_real_escape_string($conn, trim($noAssets));
$propID = mysqli_real_escape_string($conn, trim($propID));
$eventId = mysqli_real_escape_string($conn, trim($eventId));

if (empty($noAssets) || empty($propID) || empty($eventId)) {
    echo json_encode(['error' => 'Parameter noAssets, propID, dan eventId diperlukan']);
    exit;
}

try {
    // Get the selected event by eventId
    $eventQuery = "
        SELECT id, uniqID 
        FROM tblevent 
        WHERE id = '$eventId'
        AND invNo = '$noAssets' 
        AND propID = '$propID'
        AND start_date IS NOT NULL
        LIMIT 1
    ";

    $eventResult = mysqli_query($conn, $eventQuery);

    if (!$eventResult || mysqli_num_rows($eventResult) == 0) {
        echo json_encode([
            'success' => true,
            'message' => 'Tidak ada event maintenance ditemukan',
            'data' => []
        ]);
        exit;
    }

    $event = mysqli_fetch_assoc($eventResult);
    $eventId = $event['id'];
    $eventUniqId = $event['uniqID'];

    // Step 2: Get all maintenance tasks for this event
    $taskQuery = "
        SELECT * 
        FROM tblmntask 
        WHERE mntId = '$eventId' 
        AND mntUniq = '$eventUniqId'
        AND propID = '$propID'
        ORDER BY no ASC
    ";

    $taskResult = mysqli_query($conn, $taskQuery);

    if (!$taskResult) {
        throw new Exception('Database query failed: ' . mysqli_error($conn));
    }

    $maintasks = [];
    while ($row = mysqli_fetch_assoc($taskResult)) {
        $maintasks[] = $row;
    }

    if (empty($maintasks)) {
        echo json_encode([
            'success' => true,
            'message' => 'Tidak ada maintenance tasks ditemukan untuk event ini',
            'data' => []
        ]);
        exit;
    }

    // Format response data
    $response = [
        'success' => true,
        'message' => 'Data maintenance task job berhasil diambil',
        'data' => $maintasks
    ];

    echo json_encode($response);
} catch (Exception $e) {
    echo json_encode([
        'error' => 'Server error: ' . $e->getMessage()
    ]);
}

mysqli_close($conn);
