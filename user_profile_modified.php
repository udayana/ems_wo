<?php
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST');
header('Access-Control-Allow-Headers: Content-Type');

// Debug logging
error_log("user_profile.php accessed at " . date('Y-m-d H:i:s'));

// Include database connection
include 'koneksi.php';

// Check if database connection is working
if (!isset($conn)) {
    error_log("Database connection failed - \$conn not set");
    echo json_encode([
        'success' => false,
        'message' => 'Database connection failed'
    ]);
    exit;
}

try {
    // Check if it's POST request
    if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
        throw new Exception('Method not allowed');
    }

    // Get action parameter
    $action = $_POST['action'] ?? '';

    // Debug: log received data
    error_log("Received action: " . $action);
    error_log("POST data: " . print_r($_POST, true));

    if ($action === 'get') {
        // Get user profile
        $id = $_POST['id'] ?? '';

        // Validate required fields
        if (empty($id)) {
            throw new Exception('id is required');
        }

        // Get user profile data - sesuai dengan struktur tabel tbluser
        $id = mysqli_real_escape_string($conn, $id);
        $query = "SELECT id, nama, email, Telp, photoprofile, tglDaftar, active FROM tbluser WHERE id = '$id'";
        error_log("Get query: $query");

        $result = mysqli_query($conn, $query);
        if (!$result) {
            throw new Exception('Database query failed: ' . mysqli_error($conn));
        }

        $user = mysqli_fetch_assoc($result);
        if (!$user) {
            throw new Exception('User not found');
        }

        echo json_encode([
            'success' => true,
            'message' => 'User profile retrieved successfully',
            'data' => $user
        ]);
    } elseif ($action === 'update') {
        // Update user profile
        $id = $_POST['id'] ?? '';
        $fullName = $_POST['fullName'] ?? '';
        $email = $_POST['email'] ?? '';
        $phoneNumber = $_POST['phoneNumber'] ?? '';

        // Debug: log update data
        error_log("Update data - id: $id, fullName: $fullName, email: $email, phoneNumber: $phoneNumber");

        // Validate required fields
        if (empty($id)) {
            throw new Exception('id is required');
        }

        if (empty($fullName)) {
            throw new Exception('fullName is required');
        }

        if (empty($email)) {
            throw new Exception('email is required');
        }

        if (empty($phoneNumber)) {
            throw new Exception('phoneNumber is required');
        }

        // Validate email format
        if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
            throw new Exception('Invalid email format');
        }

        // Check if user exists
        $id = mysqli_real_escape_string($conn, $id);
        $query = "SELECT id FROM tbluser WHERE id = '$id'";
        $result = mysqli_query($conn, $query);
        if (!$result) {
            throw new Exception('Database query failed: ' . mysqli_error($conn));
        }

        if (!mysqli_fetch_assoc($result)) {
            throw new Exception('User not found');
        }

        // Check if email already exists for other users
        $email = mysqli_real_escape_string($conn, $email);
        $query = "SELECT id FROM tbluser WHERE email = '$email' AND id != '$id'";
        $result = mysqli_query($conn, $query);
        if (!$result) {
            throw new Exception('Database query failed: ' . mysqli_error($conn));
        }

        if (mysqli_fetch_assoc($result)) {
            throw new Exception('Email already exists');
        }

        // Update user profile - sesuai dengan struktur tabel tbluser
        $fullName = mysqli_real_escape_string($conn, $fullName);
        $phoneNumber = mysqli_real_escape_string($conn, $phoneNumber);

        $updateQuery = "UPDATE tbluser SET nama = '$fullName', email = '$email', Telp = '$phoneNumber' WHERE id = '$id'";
        error_log("Update query: $updateQuery");

        $result = mysqli_query($conn, $updateQuery);
        error_log("Update result: " . ($result ? 'true' : 'false'));

        if ($result) {
            // Get updated user data
            $query = "SELECT id, nama, email, Telp, photoprofile FROM tbluser WHERE id = '$id'";
            $result = mysqli_query($conn, $query);
            if (!$result) {
                throw new Exception('Database query failed: ' . mysqli_error($conn));
            }

            $user = mysqli_fetch_assoc($result);

            echo json_encode([
                'success' => true,
                'message' => 'Profile updated successfully',
                'data' => $user
            ]);
        } else {
            throw new Exception('Failed to update profile: ' . mysqli_error($conn));
        }
    } else {
        throw new Exception('Invalid action. Use "get" or "update"');
    }
} catch (Exception $e) {
    error_log("Exception: " . $e->getMessage());
    echo json_encode([
        'success' => false,
        'message' => $e->getMessage()
    ]);
}
