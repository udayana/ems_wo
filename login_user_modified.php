<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json");
include "config.php";

$data = json_decode(file_get_contents("php://input"));

$emailOrHP = $data->emailOrHP ?? '';
$password  = md5($data->password ?? '');

$sql = "SELECT * FROM tbluser 
        WHERE (email = ? OR Telp = ?) AND password = ? 
        LIMIT 1";
$stmt = $con->prepare($sql);
$stmt->bind_param("sss", $emailOrHP, $emailOrHP, $password);
$stmt->execute();

$result = $stmt->get_result();

if ($user = $result->fetch_assoc()) {
    echo json_encode([
        "status" => "success",
        "user" => [
            "id" => $user["id"],
            "nama" => $user["nama"],
            "email" => $user["email"],
            "dept" => $user["dept"],
            "jabatan" => $user["jabatan"],
            "photoprofile" => $user["photoprofile"],
            "propID" => $user["propID"] // ⬅️ ini WAJIB agar tidak error saat decode
        ]
    ]);
} else {
    echo json_encode(["status" => "error", "message" => "Email / Password salah"]);
}
