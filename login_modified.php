<?php
header("Content-Type: application/json");

include 'koneksi.php';

$email = $_POST['email'] ?? '';
$password = md5($_POST['password'] ?? '');


file_put_contents("log_login.txt", json_encode($_POST));


$sql = "SELECT id, nama, email, telp, propID, photoprofile,dept FROM tbluser WHERE (email = '$email' OR telp = '$email') AND password = '$password'";
$result = mysqli_query($conn, $sql);

if ($row = mysqli_fetch_assoc($result)) {
    echo json_encode($row);
} else {
    echo json_encode(["error" => "Login gagal"]);
}
