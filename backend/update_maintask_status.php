<?php
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST');
header('Access-Control-Allow-Headers: Content-Type');

include('koneksi.php');
$sekarang = date("Y-m-d");

try {
    $input = json_decode(file_get_contents('php://input'), true);

    if (!$input) {
        throw new Exception('Invalid JSON input');
    }

    $taskId = $input['taskId'] ?? '';
    $done = $input['done'] ?? 0;
    $doneby = $input['doneby'] ?? '';


    if (empty($taskId)) {
        throw new Exception('taskId is required');
    }

    // Update tblmntask menggunakan mysqli
    $sql = "UPDATE tblmntask SET 
            date='$sekarang',
            done = '$done',
            doneby = '$doneby'
            WHERE no = '$taskId'";


    $result = mysqli_query($conn, $sql);

    if ($result) {
        $affected_rows = mysqli_affected_rows($conn);


        // Ambil data mntId dan propID dari task yang diupdate
        $sql_get_info = "SELECT mntId, propID FROM tblmntask WHERE no = '$taskId'";
        $result_info = mysqli_query($conn, $sql_get_info);
        $row_info = mysqli_fetch_assoc($result_info);
        $mid = $row_info['mntId'];
        $propid = $row_info['propID'];


        //hitung berapa task dari tabel tblmntask dengan mntId yang sama
        $sql2 = "SELECT * FROM tblmntask  where propID='$propid' AND mntId ='$mid' ";
        if ($result2 = mysqli_query($conn, $sql2)) {
            $rowcount2 = mysqli_num_rows($result2);
            $jmlTask         = $rowcount2;
        }

        //hitung jumlah 'done' dari mntId yang sama
        $sql3 = "SELECT * FROM tblmntask  where propID='$propid' AND mntId ='$mid' AND done='1' ";
        if ($result3 = mysqli_query($conn, $sql3)) {
            $rowcount3 = mysqli_num_rows($result3);
            $jmlDone         = $rowcount3;
        }

        // mencari job task secara keseluruhan ditampilkan dengan variabel $katakerja
        $katakerja = '';
        $i = 0;
        $sql    = "SELECT * FROM tblmntask  where propID='$propid' AND mntId ='$mid' AND done='1' ";
        $result = mysqli_query($conn, $sql);
        if (mysqli_num_rows($result) > 0) {
            while ($row = mysqli_fetch_assoc($result)) {
                $i++;

                $katakerja = $katakerja . $i . ') ' . $row['jobtask'] . '. ';
            }
        }

        $progress = $jmlDone . '/' . $jmlTask . ' done';
        if ($jmlTask == $jmlDone) {
            $progress = 'done';
        }


        mysqli_query($conn, "update tblevent set status ='$progress', done_date='$sekarang' where id='$mid' and propID='$propid'");

        //buat baru untuk menyelesaikan tabel tblharian di sanur dan Demo
        if ($propid == 'vJCAqVcE' || $propid == 'HhfZdfuL') {

            mysqli_query($conn, "
    INSERT IGNORE INTO tblharian (tanggal, checkpoint_id, status)
    SELECT '$sekarang', checkpoint_id, 1
    FROM tblharian_checkpoint
") or die(mysqli_error($conn));
        }
        //mencari data pada tabel tblevent untuk dicatat ke history 
        $caridatamnt = mysqli_query($conn, "select * from tblevent where id ='$mid' and propID='$propid'");
        $datanya     = mysqli_fetch_array($caridatamnt);
        $invNo       = $datanya['invNo'];
        $uniqID      = $datanya['uniqID'];
        $judul       = $datanya['title'];
        $stat        = $datanya['status'];
        $by          = $datanya['created_by'];

        $rem = 'Maintenance : ' . $stat . ' - ' . $katakerja . '(by: ' . $doneby . ' )';

        //Jika jumlah done = 1    maka tabel history menjadi
        if ($jmlDone == 1) {
            mysqli_query($conn, "insert INTO tblmnthistory (no,date,propID,mntId,mntUniq, jobtask,doneby,remark) values(null,'$sekarang','$propid','$invNo','$uniqID','$judul','$doneby','$rem')");
        } elseif ($jmlDone < 1) {
            mysqli_query($conn, "delete FROM tblmnthistory  where mntId='$invNo' AND mntUniq='$uniqID' and propID='$propid'");
        } else {   // update
            mysqli_query($conn, "update tblmnthistory set doneby ='$doneby', remark ='$rem' where mntId='$invNo' AND mntUniq='$uniqID' and propID='$propid'");
        }

        echo json_encode([
            'success' => true,
            'message' => 'Maintenance task updated successfully',
            'affected_rows' => $affected_rows,
            'taskId' => $taskId,
            'done' => $done,
            'doneby' => $doneby,
            'progress' => $progress,
            'jmlTask' => $jmlTask,
            'jmlDone' => $jmlDone
        ]);
    } else {
        $error = mysqli_error($conn);
        throw new Exception('Failed to update maintenance task: ' . $error);
    }
} catch (Exception $e) {
    http_response_code(400);
    echo json_encode([
        'success' => false,
        'error' => $e->getMessage()
    ]);
}

mysqli_close($conn);
