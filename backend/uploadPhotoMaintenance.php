<?php
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

// Handle preflight request
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

// Include database connection if needed for logging
include('koneksi.php');

// Configuration
$upload_dir = '../photo/maintenance/'; // Directory untuk menyimpan file (relatif dari apiKu folder)
$max_file_size = 5 * 1024 * 1024; // 5MB max file size per foto
$allowed_types = ['image/jpeg', 'image/jpg', 'image/png'];
$max_photos = 3; // Maximum 3 photos per upload

// Create upload directory if it doesn't exist
if (!file_exists($upload_dir)) {
    if (!mkdir($upload_dir, 0755, true)) {
        http_response_code(500);
        echo json_encode([
            'success' => false,
            'error' => 'Failed to create upload directory'
        ]);
        exit();
    }
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    try {
        // Check if photos were uploaded
        if (!isset($_FILES['photos']) || !is_array($_FILES['photos']['name'])) {
            throw new Exception('No photos uploaded or invalid format');
        }

        $uploaded_files = $_FILES['photos'];
        $file_count = count($uploaded_files['name']);

        // Validate number of photos
        if ($file_count > $max_photos) {
            throw new Exception("Maximum $max_photos photos allowed. You uploaded $file_count photos.");
        }

        if ($file_count === 0) {
            throw new Exception('No photos uploaded');
        }

        $uploaded_photos = [];
        $errors = [];

        // Process each photo
        for ($i = 0; $i < $file_count; $i++) {
            // Check for upload errors
            if ($uploaded_files['error'][$i] !== UPLOAD_ERR_OK) {
                $error_messages = [
                    UPLOAD_ERR_INI_SIZE => 'File too large (server limit)',
                    UPLOAD_ERR_FORM_SIZE => 'File too large (form limit)',
                    UPLOAD_ERR_PARTIAL => 'File upload was interrupted',
                    UPLOAD_ERR_NO_FILE => 'No file uploaded',
                    UPLOAD_ERR_NO_TMP_DIR => 'Server configuration error',
                    UPLOAD_ERR_CANT_WRITE => 'Server write error',
                    UPLOAD_ERR_EXTENSION => 'Upload blocked by extension'
                ];

                $error_code = $uploaded_files['error'][$i];
                $error_msg = $error_messages[$error_code] ?? 'Unknown upload error';
                $errors[] = "Photo " . ($i + 1) . ": $error_msg";
                continue;
            }

            $file = [
                'name' => $uploaded_files['name'][$i],
                'type' => $uploaded_files['type'][$i],
                'tmp_name' => $uploaded_files['tmp_name'][$i],
                'size' => $uploaded_files['size'][$i]
            ];

            // Validate file size
            if ($file['size'] > $max_file_size) {
                $errors[] = "Photo " . ($i + 1) . " (" . $file['name'] . "): File size too large. Maximum size is 5MB.";
                continue;
            }

            // Validate file type
            $finfo = finfo_open(FILEINFO_MIME_TYPE);
            $mime_type = finfo_file($finfo, $file['tmp_name']);
            finfo_close($finfo);

            if (!in_array($mime_type, $allowed_types)) {
                $errors[] = "Photo " . ($i + 1) . " (" . $file['name'] . "): Invalid file type. Only JPEG and PNG allowed.";
                continue;
            }

            // Generate unique filename: mnt_{timestamp}_{index}.jpg (timestamp is enough for uniqueness)
            $timestamp = time();
            $file_extension = 'jpg'; // Always save as JPG
            $unique_filename = "mnt_{$timestamp}_{$i}.{$file_extension}";
            $file_path = $upload_dir . $unique_filename;

            // Process and save image
            $source_image = null;

            switch ($mime_type) {
                case 'image/jpeg':
                case 'image/jpg':
                    $source_image = imagecreatefromjpeg($file['tmp_name']);
                    break;
                case 'image/png':
                    $source_image = imagecreatefrompng($file['tmp_name']);
                    break;
                default:
                    $errors[] = "Photo " . ($i + 1) . " (" . $file['name'] . "): Unsupported image format";
                    continue;
            }

            if (!$source_image) {
                $errors[] = "Photo " . ($i + 1) . " (" . $file['name'] . "): Failed to process image";
                continue;
            }

            // Save as JPEG with quality 85%
            $success = imagejpeg($source_image, $file_path, 85);

            // Clean up
            imagedestroy($source_image);

            if (!$success) {
                $errors[] = "Photo " . ($i + 1) . " (" . $file['name'] . "): Failed to save image";
                continue;
            }

            // Verify file was created
            if (!file_exists($file_path)) {
                $errors[] = "Photo " . ($i + 1) . " (" . $file['name'] . "): File was not created";
                continue;
            }

            // Add to uploaded photos list
            $uploaded_photos[] = [
                'original_name' => $file['name'],
                'filename' => $unique_filename,
                'file_path' => 'photo/maintenance/' . $unique_filename, // Path untuk akses via web
                'file_size' => filesize($file_path),
                'mime_type' => $mime_type
            ];

            // Log the upload for debugging
            error_log("Maintenance photo uploaded: $unique_filename (" . filesize($file_path) . " bytes)");
        }

        // Check if any photos were successfully uploaded
        if (count($uploaded_photos) === 0) {
            throw new Exception('No photos were successfully uploaded. Errors: ' . implode('; ', $errors));
        }

        // Return success response
        http_response_code(200);
        echo json_encode([
            'success' => true,
            'message' => count($uploaded_photos) . ' photo(s) uploaded successfully',
            'uploaded_count' => count($uploaded_photos),
            'photos' => $uploaded_photos,
            'errors' => $errors // Include any errors for partial uploads
        ]);

    } catch (Exception $e) {
        http_response_code(400);
        echo json_encode([
            'success' => false,
            'error' => $e->getMessage()
        ]);
    }
} else {
    http_response_code(405);
    echo json_encode([
        'success' => false,
        'error' => 'Method not allowed'
    ]);
}

mysqli_close($conn);

