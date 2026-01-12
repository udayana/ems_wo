<?php
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

// Handle preflight request
if ($_SERVER['REQUEST_METHOD'] == 'OPTIONS') {
    http_response_code(200);
    exit();
}

require_once 'db_connection.php';

// Get propID from request
$propID = $_POST['propID'] ?? $_GET['propID'] ?? '';

if (empty($propID)) {
    echo json_encode([
        'success' => false,
        'message' => 'propID is required',
        'photoDone' => 0
    ]);
    exit();
}

try {
    // Query tbladmin_setting to get photoDone value
    $stmt = $conn->prepare("SELECT photoDone FROM tbladmin_setting WHERE propID = ? LIMIT 1");
    $stmt->bind_param("s", $propID);
    $stmt->execute();
    $result = $stmt->get_result();
    
    if ($result->num_rows > 0) {
        $row = $result->fetch_assoc();
        $photoDone = isset($row['photoDone']) ? (int)$row['photoDone'] : 0;
        
        echo json_encode([
            'success' => true,
            'photoDone' => $photoDone,
            'message' => 'Setting retrieved successfully'
        ]);
    } else {
        // If no setting found, default to 0 (photo optional)
        echo json_encode([
            'success' => true,
            'photoDone' => 0,
            'message' => 'Setting not found, using default value'
        ]);
    }
    
    $stmt->close();
    
} catch (Exception $e) {
    echo json_encode([
        'success' => false,
        'message' => 'Error: ' . $e->getMessage(),
        'photoDone' => 0
    ]);
}

$conn->close();
?>



































