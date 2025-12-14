<?php

/**
 * asset_schedule.php
 * Get maintenance schedules for a specific asset
 * Returns 3 nearest schedules (past, nearest, upcoming)
 */

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

include('koneksi.php');

if (!isset($conn) || !$conn) {
    http_response_code(500);
    echo json_encode(['status' => 'error', 'message' => 'Database connection failed']);
    exit();
}

$noAssets = $_GET['noAssets'] ?? $_POST['noAssets'] ?? '';
$propID = $_GET['propID'] ?? $_POST['propID'] ?? '';

if (empty($noAssets) || empty($propID)) {
    http_response_code(400);
    echo json_encode(['status' => 'error', 'message' => 'noAssets and propID are required']);
    exit();
}

$noAssets = mysqli_real_escape_string($conn, trim($noAssets));
$propID = mysqli_real_escape_string($conn, trim($propID));

// Get all schedules for this asset (including done status)
// Only select columns that exist and are needed
$query = "SELECT 
            e.id,
            e.start_date,
            e.status
          FROM tblevent e
          INNER JOIN tblinventory i ON i.No = e.invNo
          WHERE i.No = '$noAssets' 
            AND i.propID = '$propID'
          ORDER BY e.start_date ASC";

$result = mysqli_query($conn, $query);

if (!$result) {
    http_response_code(500);
    echo json_encode(['status' => 'error', 'message' => mysqli_error($conn)]);
    exit();
}

$allSchedules = [];
$today = date('Y-m-d');

while ($row = mysqli_fetch_array($result, MYSQLI_ASSOC)) {
    $startDate = $row['start_date'] ?? '';
    if (empty($startDate)) continue;

    $dateOnly = substr($startDate, 0, 10); // Get date part only

    // Format date for display
    $formattedDate = '';
    if (!empty($dateOnly)) {
        try {
            $dateObj = new DateTime($dateOnly);
            $formattedDate = $dateObj->format('d F Y');
        } catch (Exception $e) {
            $formattedDate = $dateOnly;
        }
    }

    $allSchedules[] = [
        'id' => (int)($row['id'] ?? 0),
        'start_date' => $dateOnly,
        'status' => $row['status'] ?? '',
        'formatted_date' => $formattedDate
    ];
}

mysqli_free_result($result);

// Categorize into 3 schedules: past (closest), nearest, upcoming
$pastSchedule = null;
$nearestSchedule = null;
$upcomingSchedule = null;

$pastSchedules = [];
$futureSchedules = [];

foreach ($allSchedules as $schedule) {
    $scheduleDate = $schedule['start_date'];
    if ($scheduleDate < $today) {
        $pastSchedules[] = $schedule;
    } else {
        $futureSchedules[] = $schedule;
    }
}

// Past schedule: closest to today (most recent past)
if (!empty($pastSchedules)) {
    $pastSchedule = end($pastSchedules); // Last one (closest to today)
}

// Nearest schedule: today or closest future
if (!empty($futureSchedules)) {
    $nearestSchedule = $futureSchedules[0]; // First future schedule
}

// Upcoming schedule: next after nearest
if (!empty($futureSchedules) && count($futureSchedules) > 1) {
    $upcomingSchedule = $futureSchedules[1]; // Second future schedule
}

// Build response with only the 3 schedules
$resultSchedules = [];
if ($pastSchedule) $resultSchedules[] = $pastSchedule;
if ($nearestSchedule) $resultSchedules[] = $nearestSchedule;
if ($upcomingSchedule) $resultSchedules[] = $upcomingSchedule;

echo json_encode([
    'status' => 'success',
    'data' => $resultSchedules
]);








