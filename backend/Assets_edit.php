<?php

/**
 * Assets_edit.php
 * 
 * SINGLE FILE API for Asset Inventory Management
 * All functions are contained in this file:
 * - get_locations: Get distinct locations from tblinventory
 * - search: Search assets by Property name
 * - get_detail: Get asset detail by No or Property+Lokasi
 * - update: Update asset detail (without photo)
 * - upload_photo: Upload/change asset photo separately
 * - insert: Create new asset row in tblinventory
 * - delete: Delete existing asset (and its photo) by No
 * 
 * Usage: Assets_edit.php?action=<action_name>
 * Or POST with action parameter
 */

header('Content-Type: application/json');

header('Access-Control-Allow-Origin: *');

header('Access-Control-Allow-Methods: GET, POST, OPTIONS');

header('Access-Control-Allow-Headers: Content-Type');

// Debug logging function - write to Assets_edit.txt
function debugLog($message)
{
    $logFile = __DIR__ . '/Assets_edit.txt';
    $timestamp = date('Y-m-d H:i:s');
    $logMessage = "[$timestamp] $message" . PHP_EOL;
    file_put_contents($logFile, $logMessage, FILE_APPEND);
}

// Include database connection (koneksi.php must exist in same directory)

include('koneksi.php');

// Check if connection exists

debugLog("Checking database connection...");

if (!isset($conn) || !$conn) {

    debugLog("ERROR: Database connection not established");

    http_response_code(500);

    echo json_encode(['error' => 'Database connection not established']);

    exit();
}

debugLog("Database connection OK");

// Handle preflight OPTIONS request

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {

    http_response_code(200);

    exit();
}

// Get the action parameter

$action = $_GET['action'] ?? $_POST['action'] ?? '';

// DEBUG: Log action received
debugLog("=== Assets_edit.php DEBUG ===");
debugLog("Action received: '$action'");
debugLog("Request Method: " . $_SERVER['REQUEST_METHOD']);
debugLog("GET params: " . print_r($_GET, true));
debugLog("POST params: " . print_r($_POST, true));

try {

    switch ($action) {

        case 'get_locations':

            _handleGetLocations();

            break;

        case 'search':

            _handleSearch();

            break;

        case 'get_detail':

            _handleGetDetail();

            break;

        case 'update':

            _handleUpdate();

            break;

        case 'insert':

            _handleInsert();

            break;

        case 'upload_photo':

            _handleUploadPhoto();

            break;

        case 'delete':

            _handleDelete();

            break;

        default:

            debugLog("WARNING: Invalid action received: '$action'");

            http_response_code(400);

            echo json_encode([
                'error' => 'Invalid action parameter. Supported actions: get_locations, search, get_detail, update, upload_photo, insert, delete.',
                'received_action' => $action,
                'request_method' => $_SERVER['REQUEST_METHOD']
            ]);

            break;
    }
} catch (Exception $e) {

    $errorMessage = $e->getMessage();
    $errorFile = $e->getFile();
    $errorLine = $e->getLine();

    debugLog("=== EXCEPTION CAUGHT ===");
    debugLog("Message: $errorMessage");
    debugLog("File: $errorFile");
    debugLog("Line: $errorLine");
    debugLog("Trace: " . $e->getTraceAsString());
    debugLog("=== EXCEPTION END ===");

    http_response_code(500);

    echo json_encode([
        'error' => 'Server error: ' . $errorMessage,
        'debug' => [
            'file' => $errorFile,
            'line' => $errorLine,
            'action' => $action
        ]
    ]);
}

/**

 * Handle get locations request

 * Required: propID

 */

function _handleGetLocations()

{

    global $conn;

    $propID = $_GET['propID'] ?? $_POST['propID'] ?? '';

    if (!$propID) {

        http_response_code(400);

        echo json_encode(['error' => 'propID parameter is required']);

        exit();
    }

    // Get distinct Lokasi from tblinventory

    $query = "SELECT DISTINCT Lokasi FROM tblinventory WHERE propID='$propID' AND Lokasi IS NOT NULL AND Lokasi != '' ORDER BY Lokasi ASC";

    $result = mysqli_query($conn, $query);

    if (!$result) {

        throw new Exception('Database query failed: ' . mysqli_error($conn));
    }

    $locations = [];

    while ($row = mysqli_fetch_array($result, MYSQLI_ASSOC)) {

        if (!empty($row['Lokasi'])) {

            $locations[] = $row['Lokasi'];
        }
    }

    // Return as array of strings

    echo json_encode($locations);
}

/**

 * Handle search request

 * Required: propID, search (min 2 characters)

 * Optional: lokasi

 */

function _handleSearch()

{

    global $conn;
    
    $propID = $_GET['propID'] ?? $_POST['propID'] ?? '';
    
    $searchText = $_GET['search'] ?? $_POST['search'] ?? '';
    
    $lokasi = $_GET['lokasi'] ?? $_POST['lokasi'] ?? '';

    if (!$propID) {
        
        http_response_code(400);
        
        echo json_encode(['error' => 'propID parameter is required']);
        
        exit();
    }
    
    // Trim dan deteksi wildcard "*"
    $searchText = trim($searchText);
    $isWildcardAll = ($searchText === '*');
    
    // Validasi minimal 2 karakter, KECUALI jika user memang kirim "*"
    if (!$isWildcardAll && (!$searchText || strlen($searchText) < 2)) {
        
        http_response_code(400);
        
        echo json_encode(['error' => 'Search text must be at least 2 characters']);
        
        exit();
    }
    
    // Build query
    if ($isWildcardAll) {
        // "*" = tampilkan semua asset untuk propID (dan lokasi kalau diisi)
        $query = "SELECT No, Property, Lokasi FROM tblinventory WHERE propID='$propID'";
    } else {
        $searchText = mysqli_real_escape_string($conn, $searchText);
        $query = "SELECT No, Property, Lokasi FROM tblinventory WHERE propID='$propID' AND Property LIKE '%$searchText%'";
    }
    
    if (!empty($lokasi)) {
        
        $lokasi = mysqli_real_escape_string($conn, $lokasi);
        
        $query .= " AND Lokasi='$lokasi'";
    }
    
    $query .= " ORDER BY Property ASC LIMIT 50";

    $result = mysqli_query($conn, $query);

    if (!$result) {

        throw new Exception('Database query failed: ' . mysqli_error($conn));
    }

    $assets = [];

    while ($row = mysqli_fetch_array($result, MYSQLI_ASSOC)) {

        $assets[] = [

            'No' => (int)$row['No'],

            'Property' => $row['Property'] ?? '',

            'Lokasi' => $row['Lokasi'] ?? ''

        ];
    }

    // Return as array

    echo json_encode($assets);
}

/**

 * Handle get detail request

 * Required: no

 */

function _handleGetDetail()

{

    global $conn;

    $no = $_GET['no'] ?? $_POST['no'] ?? '';

    $property = $_GET['Property'] ?? $_POST['Property'] ?? '';

    $lokasi = $_GET['Lokasi'] ?? $_POST['Lokasi'] ?? '';

    $propID = $_GET['propID'] ?? $_POST['propID'] ?? '';

    // Build query - prefer No, but support Property + Lokasi

    $query = "SELECT * FROM tblinventory WHERE ";

    if (!empty($no)) {

        // Get by No (primary key)

        $no = mysqli_real_escape_string($conn, $no);

        $query .= "No='$no'";
    } else if (!empty($property) && !empty($lokasi) && !empty($propID)) {

        // Get by Property + Lokasi + propID

        $property = mysqli_real_escape_string($conn, $property);

        $lokasi = mysqli_real_escape_string($conn, $lokasi);

        $propID = mysqli_real_escape_string($conn, $propID);

        $query .= "Property='$property' AND Lokasi='$lokasi' AND propID='$propID'";
    } else {

        http_response_code(400);

        echo json_encode(['error' => 'Either No (asset ID) OR (Property + Lokasi + propID) parameters are required']);

        exit();
    }

    $query .= " LIMIT 1";

    $result = mysqli_query($conn, $query);

    if (!$result) {

        throw new Exception('Database query failed: ' . mysqli_error($conn));
    }

    if (mysqli_num_rows($result) == 0) {

        http_response_code(404);

        echo json_encode(['error' => 'Asset not found']);

        exit();
    }

    $asset = mysqli_fetch_array($result, MYSQLI_ASSOC);

    // Prepare response

    $response = [

        'No' => (int)$asset['No'],

        'tgl' => $asset['tgl'] ?? '',

        'propID' => $asset['propID'] ?? '',

        'Category' => $asset['Category'] ?? '',

        'Lokasi' => $asset['Lokasi'] ?? null,

        'Property' => $asset['Property'] ?? null,

        'Merk' => $asset['Merk'] ?? null,

        'Model' => $asset['Model'] ?? null,

        'serno' => $asset['serno'] ?? '',

        'Capacity' => $asset['Capacity'] ?? null,

        'DatePurchased' => $asset['DatePurchased'] ?? null,

        'Suplier' => $asset['Suplier'] ?? '',

        'Keterangan' => $asset['Keterangan'] ?? '',

        'Gambar' => $asset['Gambar'] ?? null,

        'Mnt' => isset($asset['Mnt']) ? (int)$asset['Mnt'] : 0,

        'Printed' => isset($asset['Printed']) ? (int)$asset['Printed'] : 0,

        'created_at' => $asset['created_at'] ?? ''

    ];

    echo json_encode($response);
}

/**

 * Handle update request

 * Required: no, propID, Category, serno, Suplier, Keterangan

 * Optional: all other fields, photo file

 */

function _handleUpdate()

{

    global $conn;

    // DEBUG: Log all received POST parameters
    $debugInfo = [
        'action' => 'update',
        'request_method' => $_SERVER['REQUEST_METHOD'],
        'content_type' => $_SERVER['CONTENT_TYPE'] ?? 'not set',
        'post_params' => $_POST,
        'post_keys' => array_keys($_POST),
        'post_count' => count($_POST)
    ];

    debugLog("=== ASSET UPDATE DEBUG START ===");
    debugLog("POST Data: " . print_r($_POST, true));
    debugLog("Request Method: " . $_SERVER['REQUEST_METHOD']);
    debugLog("Content Type: " . ($_SERVER['CONTENT_TYPE'] ?? 'not set'));

    // Get required fields

    $no = $_POST['no'] ?? '';

    $propID = $_POST['propID'] ?? '';

    $tgl = $_POST['tgl'] ?? date('Y-m-d');

    $category = $_POST['Category'] ?? '';

    $serno = $_POST['serno'] ?? '';

    $suplier = $_POST['Suplier'] ?? '';

    $keterangan = $_POST['Keterangan'] ?? '';

    // Optional fields (removed mntld - column doesn't exist in tblinventory)

    $lokasi = $_POST['Lokasi'] ?? '';

    $property = $_POST['Property'] ?? '';

    $merk = $_POST['Merk'] ?? '';

    $model = $_POST['Model'] ?? '';

    $capacity = $_POST['Capacity'] ?? '';

    $datePurchased = $_POST['DatePurchased'] ?? '';

    // DEBUG: Log extracted values
    debugLog("Extracted values - No: $no, propID: $propID, Category: $category, serno: $serno, Suplier: $suplier, Keterangan: $keterangan");

    // Validation

    if (!$no) {

        http_response_code(400);

        $errorMsg = 'No (asset ID) is required. Received: ' . json_encode($no);

        debugLog("VALIDATION ERROR: $errorMsg");

        echo json_encode(['error' => $errorMsg, 'debug' => $debugInfo]);

        exit();
    }

    if (!$propID || !$category || !$serno || !$suplier || !$keterangan) {

        http_response_code(400);

        $missingFields = [];

        if (!$propID) $missingFields[] = 'propID';

        if (!$category) $missingFields[] = 'Category';

        if (!$serno) $missingFields[] = 'serno';

        if (!$suplier) $missingFields[] = 'Suplier';

        if (!$keterangan) $missingFields[] = 'Keterangan';

        $errorMsg = 'Missing required fields: ' . implode(', ', $missingFields);

        debugLog("VALIDATION ERROR: $errorMsg");

        echo json_encode(['error' => $errorMsg, 'missing_fields' => $missingFields, 'debug' => $debugInfo]);

        exit();
    }

    // Escape strings (only escape non-empty values, empty becomes NULL)

    $no = mysqli_real_escape_string($conn, $no);

    $propID = mysqli_real_escape_string($conn, $propID);

    $tgl = mysqli_real_escape_string($conn, $tgl);

    $category = mysqli_real_escape_string($conn, $category);

    $serno = mysqli_real_escape_string($conn, $serno);

    $suplier = mysqli_real_escape_string($conn, $suplier);

    $keterangan = mysqli_real_escape_string($conn, $keterangan);

    // Handle optional fields - convert empty string to NULL (removed mntld)

    $lokasi = !empty($lokasi) ? mysqli_real_escape_string($conn, $lokasi) : null;

    $property = !empty($property) ? mysqli_real_escape_string($conn, $property) : null;

    $merk = !empty($merk) ? mysqli_real_escape_string($conn, $merk) : null;

    $model = !empty($model) ? mysqli_real_escape_string($conn, $model) : null;

    $capacity = !empty($capacity) ? mysqli_real_escape_string($conn, $capacity) : null;

    $datePurchased = !empty($datePurchased) ? mysqli_real_escape_string($conn, $datePurchased) : null;

    // Build UPDATE query parts (NO PHOTO UPLOAD - use upload_photo action separately)

    $setParts = [];

    $setParts[] = "tgl='$tgl'";

    $setParts[] = "propID='$propID'";

    $setParts[] = "Category='$category'";

    $setParts[] = "serno='$serno'";

    $setParts[] = "Suplier='$suplier'";

    $setParts[] = "Keterangan='$keterangan'";

    // Add optional fields (removed mntld - column doesn't exist)

    if ($lokasi !== null && $lokasi !== '') {

        $setParts[] = "Lokasi='$lokasi'";
    } else {

        $setParts[] = "Lokasi=NULL";
    }

    if ($property !== null && $property !== '') {

        $setParts[] = "Property='$property'";
    } else {

        $setParts[] = "Property=NULL";
    }

    if ($merk !== null && $merk !== '') {

        $setParts[] = "Merk='$merk'";
    } else {

        $setParts[] = "Merk=NULL";
    }

    if ($model !== null && $model !== '') {

        $setParts[] = "Model='$model'";
    } else {

        $setParts[] = "Model=NULL";
    }

    if ($capacity !== null && $capacity !== '') {

        $setParts[] = "Capacity='$capacity'";
    } else {

        $setParts[] = "Capacity=NULL";
    }

    if ($datePurchased !== null && $datePurchased !== '') {

        $setParts[] = "DatePurchased='$datePurchased'";
    } else {

        $setParts[] = "DatePurchased=NULL";
    }

    // Build final query - NO PHOTO UPLOAD HERE

    $setClause = implode(', ', $setParts);

    $query = "UPDATE tblinventory SET $setClause WHERE No='$no'";

    // DEBUG: Log the query
    debugLog("SQL Query: $query");
    debugLog("Query parts count: " . count($setParts));

    // Execute query

    $result = mysqli_query($conn, $query);

    if (!$result) {

        $errorMsg = mysqli_error($conn);

        $errorCode = mysqli_errno($conn);

        debugLog("DATABASE QUERY FAILED - Error: $errorMsg (Code: $errorCode)");

        debugLog("Failed Query: $query");

        http_response_code(500);

        echo json_encode([

            'error' => 'Database query failed',

            'message' => $errorMsg,

            'code' => $errorCode,

            'query' => $query,

            'debug' => [

                'no' => $no,

                'set_clause' => $setClause,

                'set_parts_count' => count($setParts)

            ]

        ]);

        debugLog("=== ASSET UPDATE DEBUG END (ERROR) ===");

        exit();
    }

    $affectedRows = mysqli_affected_rows($conn);

    debugLog("Query executed. Affected rows: $affectedRows");

    if ($affectedRows > 0) {

        debugLog("=== ASSET UPDATE SUCCESS ===");

        echo json_encode([

            'success' => true,

            'message' => 'Asset updated successfully',

            'affected_rows' => $affectedRows

        ]);
    } else {

        debugLog("WARNING: Query executed but no rows affected. Asset No: $no");

        http_response_code(400);

        echo json_encode([

            'error' => 'No changes made or asset not found',

            'debug' => [

                'no' => $no,

                'affected_rows' => $affectedRows,

                'query' => $query

            ]

        ]);
    }

    debugLog("=== ASSET UPDATE DEBUG END ===");
}

/**
 * Handle insert (create new asset) request
 * Required: propID, Category, serno, Suplier, Keterangan
 * Optional: Lokasi, Property, Merk, Model, Capacity, DatePurchased
 */
function _handleInsert()
{
    global $conn;

    // Required fields
    $propID = $_POST['propID'] ?? '';
    $category = $_POST['Category'] ?? '';
    $serno = $_POST['serno'] ?? '';
    $suplier = $_POST['Suplier'] ?? '';
    $keterangan = $_POST['Keterangan'] ?? '';

    // Optional fields
    $lokasi = $_POST['Lokasi'] ?? '';
    $property = $_POST['Property'] ?? '';
    $merk = $_POST['Merk'] ?? '';
    $model = $_POST['Model'] ?? '';
    $capacity = $_POST['Capacity'] ?? '';
    $datePurchased = $_POST['DatePurchased'] ?? '';

    if (!$propID || !$category || !$serno || !$suplier || !$keterangan) {
        http_response_code(400);
        $missing = [];
        if (!$propID) $missing[] = 'propID';
        if (!$category) $missing[] = 'Category';
        if (!$serno) $missing[] = 'serno';
        if (!$suplier) $missing[] = 'Suplier';
        if (!$keterangan) $missing[] = 'Keterangan';
        echo json_encode([
            'error' => 'Missing required fields: ' . implode(', ', $missing),
            'missing_fields' => $missing
        ]);
        exit();
    }

    // Escape values
    $propID = mysqli_real_escape_string($conn, $propID);
    $category = mysqli_real_escape_string($conn, $category);
    $serno = mysqli_real_escape_string($conn, $serno);
    $suplier = mysqli_real_escape_string($conn, $suplier);
    $keterangan = mysqli_real_escape_string($conn, $keterangan);

    $lokasi = !empty($lokasi) ? mysqli_real_escape_string($conn, $lokasi) : null;
    $property = !empty($property) ? mysqli_real_escape_string($conn, $property) : null;
    $merk = !empty($merk) ? mysqli_real_escape_string($conn, $merk) : null;
    $model = !empty($model) ? mysqli_real_escape_string($conn, $model) : null;
    $capacity = !empty($capacity) ? mysqli_real_escape_string($conn, $capacity) : null;
    $datePurchased = !empty($datePurchased) ? mysqli_real_escape_string($conn, $datePurchased) : null;

    $tgl = date('Y-m-d');

    $columns = ['propID', 'tgl', 'Category', 'serno', 'Suplier', 'Keterangan'];
    $values = ["'$propID'", "'$tgl'", "'$category'", "'$serno'", "'$suplier'", "'$keterangan'"];

    if ($lokasi !== null) {
        $columns[] = 'Lokasi';
        $values[] = "'$lokasi'";
    }
    if ($property !== null) {
        $columns[] = 'Property';
        $values[] = "'$property'";
    }
    if ($merk !== null) {
        $columns[] = 'Merk';
        $values[] = "'$merk'";
    }
    if ($model !== null) {
        $columns[] = 'Model';
        $values[] = "'$model'";
    }
    if ($capacity !== null) {
        $columns[] = 'Capacity';
        $values[] = "'$capacity'";
    }
    if ($datePurchased !== null) {
        $columns[] = 'DatePurchased';
        $values[] = "'$datePurchased'";
    }

    $columnsSql = implode(',', $columns);
    $valuesSql = implode(',', $values);

    $query = "INSERT INTO tblinventory ($columnsSql) VALUES ($valuesSql)";

    debugLog("INSERT query: $query");

    $result = mysqli_query($conn, $query);
    if (!$result) {
        http_response_code(500);
        echo json_encode([
            'error' => 'Database insert failed: ' . mysqli_error($conn)
        ]);
        exit();
    }

    $newNo = mysqli_insert_id($conn);

    echo json_encode([
        'success' => true,
        'message' => 'Asset created successfully',
        'No' => (int)$newNo
    ]);
}

/**
 *
 * Handle upload photo request - UPLOAD PHOTO ONLY
 *
 * Required: no, photo file
 *
 */

function _handleUploadPhoto()

{

    global $conn;

    // Get required fields
    $no = $_POST['no'] ?? '';

    // Trim whitespace and validate
    $no = trim($no);

    debugLog("=== PHOTO UPLOAD DEBUG START ===");
    debugLog("Raw No from POST: " . var_export($_POST['no'] ?? 'NOT SET', true));
    debugLog("Trimmed No: '$no'");
    debugLog("No type: " . gettype($no));
    debugLog("No length: " . strlen($no));

    if (!$no) {
        http_response_code(400);
        echo json_encode(['error' => 'No (asset ID) is required']);
        exit();
    }

    // Validate No is numeric
    if (!is_numeric($no)) {
        debugLog("ERROR: No is not numeric: '$no'");
        http_response_code(400);
        echo json_encode(['error' => 'Invalid asset ID format']);
        exit();
    }

    // Convert to integer for consistency
    $noInt = (int)$no;
    debugLog("No as integer: $noInt");

    // Check if photo file is provided
    if (!isset($_FILES['photo']) || $_FILES['photo']['error'] !== UPLOAD_ERR_OK) {
        http_response_code(400);
        echo json_encode(['error' => 'Photo file is required']);
        exit();
    }

    // Upload directory - maintenance/photo folder
    // Path: https://emshotels.net/admin/pages/maintenance/photo/

    $uploadDir = __DIR__ . '/../admin/pages/maintenance/photo/';

    if (!is_dir($uploadDir)) {

        $uploadDir = __DIR__ . '/../../admin/pages/maintenance/photo/';

        if (!is_dir($uploadDir)) {

            debugLog("ERROR: Upload directory not found. Tried: " . __DIR__ . '/../admin/pages/maintenance/photo/');
            debugLog("ERROR: Also tried: " . __DIR__ . '/../../admin/pages/maintenance/photo/');
            http_response_code(500);

            echo json_encode(['error' => 'Upload directory not found: ' . $uploadDir]);

            exit();
        }
    }

    // Log upload directory info
    debugLog("Upload directory: $uploadDir");
    debugLog("Directory exists: " . (is_dir($uploadDir) ? 'YES' : 'NO'));
    debugLog("Directory writable: " . (is_writable($uploadDir) ? 'YES' : 'NO'));
    debugLog("Directory absolute path: " . realpath($uploadDir));

    $fileExtension = strtolower(pathinfo($_FILES['photo']['name'], PATHINFO_EXTENSION));

    $allowedExtensions = ['jpg', 'jpeg', 'png', 'gif'];

    if (!in_array($fileExtension, $allowedExtensions)) {

        http_response_code(400);

        echo json_encode(['error' => 'Invalid file type. Only JPG, JPEG, PNG, GIF are allowed']);

        exit();
    }

    // Check file size (max 10MB for main image - should be smaller after client resize)
    $maxFileSize = 10 * 1024 * 1024; // 10MB

    if ($_FILES['photo']['size'] > $maxFileSize) {
        http_response_code(400);
        echo json_encode(['error' => 'File too large. Maximum size is 10MB']);
        exit();
    }

    debugLog("Main image size: " . $_FILES['photo']['size'] . " bytes");
    debugLog("Main image name: " . $_FILES['photo']['name']);

    if (isset($_FILES['thumb'])) {
        debugLog("Thumbnail size: " . $_FILES['thumb']['size'] . " bytes");
        debugLog("Thumbnail name: " . $_FILES['thumb']['name']);
    }

    // Filename format: assets_[no].jpg - ALWAYS SAME NAME based on No for replacement
    $filename = 'assets_' . $noInt . '.jpg';
    $uploadPath = $uploadDir . $filename;

    debugLog("Target filename: $filename");
    debugLog("Full upload path: $uploadPath");

    // Create thumb directory if it doesn't exist
    $thumbDir = $uploadDir . 'thumb/';
    if (!is_dir($thumbDir)) {
        if (!mkdir($thumbDir, 0755, true)) {
            debugLog("Warning: Failed to create thumb directory: $thumbDir");
        } else {
            debugLog("Created thumb directory: $thumbDir");
        }
    }

    // Check if old file exists and delete it (for clean replacement)
    if (file_exists($uploadPath)) {
        debugLog("Old file exists, deleting: $uploadPath");
        if (@unlink($uploadPath)) {
            debugLog("Old file deleted successfully");
        } else {
            debugLog("Warning: Failed to delete old file, but will try to overwrite");
        }
    }

    // Move main image (already resized to 480px short side in client)
    // move_uploaded_file will automatically replace if file exists
    debugLog("Attempting to move uploaded file from: " . $_FILES['photo']['tmp_name'] . " to: $uploadPath");

    if (!move_uploaded_file($_FILES['photo']['tmp_name'], $uploadPath)) {
        $error = error_get_last();
        debugLog("FAILED to upload main image to: $uploadPath");
        debugLog("Error details: " . ($error ? print_r($error, true) : 'No error details'));
        debugLog("Upload directory writable: " . (is_writable($uploadDir) ? 'YES' : 'NO'));
        debugLog("Temporary file exists: " . (file_exists($_FILES['photo']['tmp_name']) ? 'YES' : 'NO'));
        http_response_code(500);
        echo json_encode(['error' => 'Failed to upload photo to server']);
        exit();
    }

    // Verify file was actually uploaded
    if (!file_exists($uploadPath)) {
        debugLog("ERROR: File move reported success but file not found at: $uploadPath");
        http_response_code(500);
        echo json_encode(['error' => 'File upload verification failed']);
        exit();
    }

    $fileSize = filesize($uploadPath);
    debugLog("Main image uploaded successfully: $filename");
    debugLog("Uploaded file size: $fileSize bytes");
    debugLog("File exists and verified at: $uploadPath");

    // Handle thumbnail if provided (already resized to 100x100px in client)
    // Thumbnail format: thumb_assets_[no].jpg - ALWAYS SAME NAME based on No
    $thumbFilename = 'thumb_assets_' . $noInt . '.jpg';
    $thumbPath = $thumbDir . $thumbFilename;

    debugLog("Thumbnail filename: $thumbFilename");
    debugLog("Full thumbnail path: $thumbPath");

    if (isset($_FILES['thumb']) && $_FILES['thumb']['error'] === UPLOAD_ERR_OK) {
        // Delete old thumbnail if exists
        if (file_exists($thumbPath)) {
            debugLog("Old thumbnail exists, deleting: $thumbPath");
            @unlink($thumbPath);
        }

        if (!move_uploaded_file($_FILES['thumb']['tmp_name'], $thumbPath)) {
            debugLog("Warning: Failed to upload thumbnail, but main image saved");
            // Don't fail if thumbnail upload fails
        } else {
            // Verify thumbnail was uploaded
            if (file_exists($thumbPath)) {
                $thumbSize = filesize($thumbPath);
                debugLog("Thumbnail uploaded successfully: $thumbFilename");
                debugLog("Uploaded thumbnail size: $thumbSize bytes");
            } else {
                debugLog("Warning: Thumbnail move reported success but file not found");
            }
        }
    }

    // Update database with new photo filename
    $gambar = mysqli_real_escape_string($conn, $filename);

    debugLog("Preparing to update database...");
    debugLog("No (integer): $noInt");
    debugLog("Gambar filename: $gambar");

    // First, check if the record exists using prepared statement
    $checkQuery = "SELECT No, Gambar FROM tblinventory WHERE No = ? LIMIT 1";
    debugLog("Checking if asset exists - Query: $checkQuery (No = $noInt)");

    $checkStmt = mysqli_prepare($conn, $checkQuery);
    if (!$checkStmt) {
        $errorMsg = mysqli_error($conn);
        debugLog("Error preparing check query: $errorMsg");
        http_response_code(500);
        echo json_encode(['error' => 'Database check preparation failed: ' . $errorMsg]);
        exit();
    }

    mysqli_stmt_bind_param($checkStmt, "i", $noInt);
    mysqli_stmt_execute($checkStmt);
    $checkResult = mysqli_stmt_get_result($checkStmt);

    if (!$checkResult) {
        $errorMsg = mysqli_error($conn);
        debugLog("Error executing check query: $errorMsg");
        mysqli_stmt_close($checkStmt);
        http_response_code(500);
        echo json_encode(['error' => 'Database check failed: ' . $errorMsg]);
        exit();
    }

    $existingRecord = mysqli_fetch_assoc($checkResult);
    mysqli_stmt_close($checkStmt);

    if (!$existingRecord) {
        debugLog("Asset with No=$noInt does not exist in database");
        debugLog("However, files have been uploaded successfully:");
        debugLog("  - Main image: $uploadPath (exists: " . (file_exists($uploadPath) ? 'YES' : 'NO') . ")");
        debugLog("  - Thumbnail: $thumbPath (exists: " . (file_exists($thumbPath) ? 'YES' : 'NO') . ")");
        // Don't delete files - they were uploaded successfully, just DB record not found
        // Files remain on server even if DB record doesn't exist
        http_response_code(400);
        echo json_encode([
            'error' => 'Asset not found in database',
            'warning' => 'Files were uploaded but database record not found. Files remain on server.'
        ]);
        exit();
    }

    debugLog("Asset exists. Current Gambar value: " . ($existingRecord['Gambar'] ?? 'NULL'));
    debugLog("Proceeding with UPDATE...");

    // Now update the record using prepared statement
    $updateQuery = "UPDATE tblinventory SET Gambar = ? WHERE No = ?";
    debugLog("UPDATE Query: $updateQuery (Gambar = '$gambar', No = $noInt)");

    $updateStmt = mysqli_prepare($conn, $updateQuery);
    if (!$updateStmt) {
        $errorMsg = mysqli_error($conn);
        debugLog("Error preparing update query: $errorMsg");
        http_response_code(500);
        echo json_encode(['error' => 'Database update preparation failed: ' . $errorMsg]);
        exit();
    }

    mysqli_stmt_bind_param($updateStmt, "si", $gambar, $noInt);
    $updateSuccess = mysqli_stmt_execute($updateStmt);

    if (!$updateSuccess) {
        $errorMsg = mysqli_stmt_error($updateStmt);
        debugLog("Database update execution failed: $errorMsg");
        mysqli_stmt_close($updateStmt);
        // Delete uploaded files if database update fails
        @unlink($uploadPath);
        @unlink($thumbPath);
        http_response_code(500);
        echo json_encode(['error' => 'Database update failed: ' . $errorMsg]);
        exit();
    }

    $affectedRows = mysqli_stmt_affected_rows($updateStmt);
    mysqli_stmt_close($updateStmt);

    debugLog("Affected rows: $affectedRows");

    if ($affectedRows > 0) {
        debugLog("Photo upload successful for asset No: $noInt");
        debugLog("Files uploaded:");
        debugLog("  - Main image: $uploadPath (size: " . (file_exists($uploadPath) ? filesize($uploadPath) : 0) . " bytes)");
        debugLog("  - Thumbnail: $thumbPath (size: " . (file_exists($thumbPath) ? filesize($thumbPath) : 0) . " bytes)");
        echo json_encode([
            'success' => true,
            'message' => 'Photo uploaded successfully',
            'gambar' => $filename
        ]);
    } else {
        // This shouldn't happen if check passed, but handle it anyway
        debugLog("Warning: UPDATE executed but no rows affected for No: $noInt");
        debugLog("Current Gambar value might already be: $filename");
        debugLog("However, files are uploaded and verified on server:");
        debugLog("  - Main image: $uploadPath (exists: " . (file_exists($uploadPath) ? 'YES' : 'NO') . ")");
        debugLog("  - Thumbnail: $thumbPath (exists: " . (file_exists($thumbPath) ? 'YES' : 'NO') . ")");

        // Don't delete files - they were uploaded successfully
        // Return success anyway since files are uploaded and will replace old ones
        echo json_encode([
            'success' => true,
            'message' => 'Photo uploaded successfully (no database change needed)',
            'gambar' => $filename,
            'warning' => 'No database rows affected - image may already be set'
        ]);
    }

    debugLog("=== PHOTO UPLOAD DEBUG END ===");
}

/**
 * Handle delete asset request
 * Required: no
 */
function _handleDelete()
{
    global $conn;

    $no = $_POST['no'] ?? '';
    $no = trim($no);

    if (!$no || !is_numeric($no)) {
        http_response_code(400);
        echo json_encode(['error' => 'Valid no (asset ID) is required']);
        exit();
    }

    $noInt = (int)$no;

    // Optionally delete photo files
    $uploadDir = __DIR__ . '/../admin/pages/maintenance/photo/';
    if (!is_dir($uploadDir)) {
        $uploadDir = __DIR__ . '/../../admin/pages/maintenance/photo/';
    }

    $mainFile = $uploadDir . 'assets_' . $noInt . '.jpg';
    $thumbFile = $uploadDir . 'thumb/thumb_assets_' . $noInt . '.jpg';

    if (file_exists($mainFile)) {
        @unlink($mainFile);
    }
    if (file_exists($thumbFile)) {
        @unlink($thumbFile);
    }

    $noEscaped = mysqli_real_escape_string($conn, (string)$noInt);
    $query = "DELETE FROM tblinventory WHERE No='$noEscaped' LIMIT 1";

    $result = mysqli_query($conn, $query);
    if (!$result) {
        http_response_code(500);
        echo json_encode([
            'error' => 'Database delete failed: ' . mysqli_error($conn)
        ]);
        exit();
    }

    $affected = mysqli_affected_rows($conn);
    if ($affected <= 0) {
        http_response_code(404);
        echo json_encode([
            'error' => 'Asset not found or already deleted'
        ]);
        exit();
    }

    echo json_encode([
        'success' => true,
        'message' => 'Asset deleted successfully',
        'No' => $noInt
    ]);
}
