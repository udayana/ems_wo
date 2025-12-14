<?php
// Simple web-based Asset Editor that mirrors the Android AssetFragment behavior
// Uses the same API endpoints in Assets_edit.php without modification.
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Asset Editor</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            margin: 0;
            padding: 0;
            background: #f5f5f5;
            color: #333;
        }
        header {
            background: #1976d2;
            color: #fff;
            padding: 12px 20px;
        }
        header h1 {
            margin: 0;
            font-size: 20px;
        }
        .container {
            max-width: 1080px;
            margin: 20px auto;
            padding: 16px;
            background: #fff;
            box-shadow: 0 2px 6px rgba(0,0,0,0.08);
            border-radius: 6px;
        }
        .section-title {
            font-size: 16px;
            font-weight: 600;
            margin: 0 0 8px 0;
        }
        .row {
            display: flex;
            flex-wrap: wrap;
            gap: 12px;
            margin-bottom: 12px;
        }
        .field {
            display: flex;
            flex-direction: column;
            flex: 1;
            min-width: 160px;
        }
        .field label {
            font-size: 13px;
            margin-bottom: 4px;
        }
        .field input[type="text"],
        .field input[type="date"],
        .field textarea,
        .field select {
            padding: 6px 8px;
            border-radius: 4px;
            border: 1px solid #ccc;
            font-size: 13px;
        }
        .field textarea {
            resize: vertical;
            min-height: 60px;
        }
        .btn {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            padding: 6px 14px;
            border-radius: 4px;
            border: none;
            font-size: 13px;
            cursor: pointer;
            background: #e0e0e0;
            color: #333;
        }
        .btn-primary {
            background: #1976d2;
            color: #fff;
        }
        .btn-secondary {
            background: #4caf50;
            color: #fff;
        }
        .btn-danger {
            background: #f44336;
            color: #fff;
        }
        .btn:disabled {
            opacity: 0.6;
            cursor: default;
        }
        .search-results {
            margin-top: 8px;
            border: 1px solid #ddd;
            border-radius: 4px;
            max-height: 260px;
            overflow: auto;
        }
        .search-results table {
            width: 100%;
            border-collapse: collapse;
            font-size: 13px;
        }
        .search-results th,
        .search-results td {
            padding: 6px 8px;
            border-bottom: 1px solid #eee;
        }
        .search-results th {
            background: #fafafa;
            position: sticky;
            top: 0;
            z-index: 1;
        }
        .search-row {
            cursor: pointer;
        }
        .search-row:hover {
            background: #e3f2fd;
        }
        .detail-section {
            margin-top: 20px;
            border-top: 1px solid #eee;
            padding-top: 16px;
            display: none;
        }
        .image-preview {
            width: 220px;
            height: 220px;
            border-radius: 4px;
            border: 1px solid #ddd;
            background: #fafafa center center / cover no-repeat;
            display: flex;
            align-items: center;
            justify-content: center;
            overflow: hidden;
        }
        .image-preview img {
            max-width: 100%;
            max-height: 100%;
            object-fit: contain;
        }
        .muted {
            color: #777;
            font-size: 12px;
        }
        .status-bar {
            margin-top: 8px;
            font-size: 13px;
        }
        .status-bar span {
            display: inline-block;
            margin-right: 12px;
        }
        .status-ok {
            color: #2e7d32;
        }
        .status-error {
            color: #c62828;
        }
        @media (max-width: 768px) {
            .row {
                flex-direction: column;
            }
            .image-preview {
                width: 100%;
                max-width: 260px;
                margin-bottom: 8px;
            }
        }
    </style>
</head>
<body>
<header>
    <h1>Asset Editor (Web Version)</h1>
</header>

<div class="container">
    <div>
        <p class="section-title">1. Filter &amp; Pencarian</p>
        <!-- propID diset hardcoded dan disembunyikan dari user -->
        <input type="hidden" id="propID" value="Vqhi8YmD">
        <div class="row">
            <div class="field" style="max-width: 220px;">
                <label for="lokasiFilter">Lokasi</label>
                <select id="lokasiFilter">
                    <option value="">All Locations</option>
                </select>
            </div>
            <div class="field">
                <label for="searchText">Cari Property (min 2 huruf)</label>
                <input type="text" id="searchText" placeholder="Nama asset / property">
            </div>
        </div>
        <div class="row">
            <button class="btn" id="btnSearch">Search</button>
            <span class="muted" id="searchInfo">Ketik minimal 2 karakter untuk mulai mencari</span>
        </div>
        <div class="search-results" id="searchResultsWrapper" style="display:none;">
            <table>
                <thead>
                <tr>
                    <th style="width:60px;">No</th>
                    <th>Property</th>
                    <th>Lokasi</th>
                </tr>
                </thead>
                <tbody id="searchResultsBody">
                </tbody>
            </table>
        </div>
    </div>

    <div class="detail-section" id="detailSection">
        <p class="section-title">2. Detail Asset</p>
        <div class="row">
            <div class="field" style="max-width: 240px;">
                <label>No (Primary Key)</label>
                <input type="text" id="detailNo" readonly>
            </div>
            <!-- propID untuk detail disimpan sebagai hidden, tidak terlihat oleh user -->
            <input type="hidden" id="detailPropID">
        </div>

        <div class="row">
            <div class="field">
                <label>Category</label>
                <input type="text" id="detailCategory">
            </div>
            <div class="field">
                <label>Lokasi</label>
                <input type="text" id="detailLokasi">
            </div>
            <div class="field">
                <label>Property</label>
                <input type="text" id="detailProperty">
            </div>
        </div>

        <div class="row">
            <div class="field">
                <label>Merk</label>
                <input type="text" id="detailMerk">
            </div>
            <div class="field">
                <label>Model</label>
                <input type="text" id="detailModel">
            </div>
            <div class="field">
                <label>Serial Number (serno)</label>
                <input type="text" id="detailSerno">
            </div>
        </div>

        <div class="row">
            <div class="field">
                <label>Capacity</label>
                <input type="text" id="detailCapacity">
            </div>
            <div class="field">
                <label>Date Purchased</label>
                <input type="date" id="detailDatePurchased">
            </div>
            <div class="field">
                <label>Suplier</label>
                <input type="text" id="detailSuplier">
            </div>
        </div>

        <div class="row">
            <div class="field">
                <label>Keterangan</label>
                <textarea id="detailKeterangan"></textarea>
            </div>
        </div>

        <p class="section-title">3. Photo Asset</p>
        <div class="row">
            <div class="field" style="max-width: 260px;">
                <div class="image-preview" id="imagePreview">
                    <span class="muted">No Image</span>
                </div>
                <div class="row" style="margin-top:8px; gap:6px; align-items: center;">
                    <input type="file" id="photoInput" accept="image/*">
                    <button class="btn btn-primary" id="btnUploadPhoto">UPLOAD</button>
                </div>
                <div class="muted" style="margin-top:4px;">
                    Pilih photo &amp; klik UPLOAD untuk kirim ke server.<br>
                    SAVE DATA hanya untuk menyimpan detail teks.
                </div>
            </div>
            <div class="field">
                <label>&nbsp;</label>
                <div class="row" style="gap:8px;">
                    <button class="btn btn-secondary" id="btnSaveData">SAVE DATA</button>
                </div>
                <div class="status-bar" id="statusBar"></div>
            </div>
        </div>
    </div>
</div>

<script>
    // Base URL untuk foto di server (sama seperti Android)
    const PHOTO_BASE_URL = "https://emshotels.net/admin/pages/maintenance/photo/";

    // Hardcoded propID sesuai permintaan
    const HARDCODED_PROP_ID = "Vqhi8YmD";

    let currentAssetDetail = null;
    let selectedImageFile = null;
    let selectedThumbBlob = null;

    function setStatus(message, isError = false) {
        const el = document.getElementById('statusBar');
        if (!el) return;
        el.innerHTML = '';
        const span = document.createElement('span');
        span.textContent = message;
        span.className = isError ? 'status-error' : 'status-ok';
        el.appendChild(span);
    }

    function appendLog(message) {
        const el = document.getElementById('statusBar');
        if (!el) return;
        const span = document.createElement('span');
        span.textContent = message;
        el.appendChild(span);
    }

    async function postFormData(formData) {
        // Panggil endpoint yang sudah ada di folder apiKu (tidak membuat endpoint baru)
        const response = await fetch('/apiKu/Assets_edit.php', {
            method: 'POST',
            body: formData
        });
        const text = await response.text();
        let json;
        try {
            json = JSON.parse(text);
        } catch (e) {
            throw new Error('Invalid JSON response: ' + text);
        }
        if (!response.ok) {
            throw new Error(json.error || ('HTTP ' + response.status));
        }
        return json;
    }

    // LOAD LOCATIONS OTOMATIS BERDASARKAN propID (tanpa tombol)
    async function autoLoadLocations() {
        const propID = HARDCODED_PROP_ID;
        if (!propID) {
            return;
        }
        try {
            const fd = new FormData();
            fd.append('action', 'get_locations');
            fd.append('propID', propID);
            const locations = await postFormData(fd);
            const select = document.getElementById('lokasiFilter');
            select.innerHTML = '';
            const optAll = document.createElement('option');
            optAll.value = '';
            optAll.textContent = 'All Locations';
            select.appendChild(optAll);
            locations.forEach(loc => {
                const opt = document.createElement('option');
                opt.value = loc;
                opt.textContent = loc;
                select.appendChild(opt);
            });
            setStatus('Locations loaded (' + locations.length + ')');
        } catch (e) {
            setStatus('Error loading locations: ' + e.message, true);
        }
    }

    // Panggil autoLoadLocations saat halaman selesai dimuat
    window.addEventListener('DOMContentLoaded', autoLoadLocations);

    // SEARCH - otomatis dengan AJAX saat minimal 2 karakter diketik
    let searchTimeout = null;

    document.getElementById('btnSearch').addEventListener('click', () => {
        // Tombol hanya sebagai pemicu manual tambahan
        performSearch();
    });

    document.getElementById('searchText').addEventListener('keyup', function () {
        const text = this.value.trim();
        const infoEl = document.getElementById('searchInfo');

        // Jika kurang dari 2 karakter, jangan search dan kosongkan hasil
        if (text.length < 2) {
            infoEl.textContent = 'Ketik minimal 2 karakter untuk mulai mencari';
            clearTimeout(searchTimeout);
            renderSearchResults([]);
            return;
        }

        infoEl.textContent = 'Mencari...';

        // Debounce: tunggu 500ms setelah user berhenti mengetik
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            performSearch();
        }, 500);
    });

    async function performSearch() {
        const propID = HARDCODED_PROP_ID;
        const searchText = document.getElementById('searchText').value.trim();
        const lokasi = document.getElementById('lokasiFilter').value.trim();

        if (!propID) {
            alert('Isi propID terlebih dahulu');
            return;
        }
        if (searchText.length < 2) {
            // Sudah dicek di keyup, tapi jaga-jaga
            document.getElementById('searchInfo').textContent = 'Ketik minimal 2 karakter untuk mulai mencari';
            renderSearchResults([]);
            return;
        }

        setStatus('');

        try {
            const fd = new FormData();
            fd.append('action', 'search');
            fd.append('propID', propID);
            fd.append('search', searchText);
            if (lokasi) {
                fd.append('lokasi', lokasi);
            }
            const results = await postFormData(fd);
            renderSearchResults(results || []);
            setStatus('Search OK: ' + (results ? results.length : 0) + ' assets');
            document.getElementById('searchInfo').textContent =
                (results && results.length > 0)
                    ? 'Ditemukan ' + results.length + ' asset'
                    : 'Tidak ada asset yang cocok';
        } catch (e) {
            setStatus('Error search: ' + e.message, true);
            alert('Error search: ' + e.message);
            renderSearchResults([]);
        }
    }

    function renderSearchResults(results) {
        const wrapper = document.getElementById('searchResultsWrapper');
        const tbody = document.getElementById('searchResultsBody');
        tbody.innerHTML = '';
        if (!results || results.length === 0) {
            wrapper.style.display = 'none';
            return;
        }
        results.forEach(row => {
            const tr = document.createElement('tr');
            tr.className = 'search-row';
            const tdNo = document.createElement('td');
            const tdProp = document.createElement('td');
            const tdLok = document.createElement('td');
            tdNo.textContent = row.No ?? row.no ?? '';
            tdProp.textContent = row.Property ?? '';
            tdLok.textContent = row.Lokasi ?? '';
            tr.appendChild(tdNo);
            tr.appendChild(tdProp);
            tr.appendChild(tdLok);
            tr.addEventListener('click', () => {
                const noVal = row.No ?? row.no;
                if (!noVal) {
                    alert('Asset No tidak ditemukan di hasil search');
                    return;
                }
                loadAssetDetail(parseInt(noVal, 10));
            });
            tbody.appendChild(tr);
        });
        wrapper.style.display = 'block';
        document.getElementById('detailSection').style.display = 'none';
    }

    // LOAD DETAIL
    async function loadAssetDetail(no) {
        const propID = HARDCODED_PROP_ID;
        if (!propID) {
            alert('Isi propID terlebih dahulu');
            return;
        }
        setStatus('Loading asset detail No ' + no + ' ...');
        try {
            const fd = new FormData();
            fd.append('action', 'get_detail');
            fd.append('no', String(no));
            fd.append('propID', propID);
            const detail = await postFormData(fd);
            currentAssetDetail = detail;
            fillDetailForm(detail, propID);
            setStatus('Detail loaded for asset No ' + no);
        } catch (e) {
            setStatus('Error load detail: ' + e.message, true);
            alert('Error load detail: ' + e.message);
        }
    }

    function fillDetailForm(asset, propID) {
        document.getElementById('detailNo').value = asset.No ?? '';
        document.getElementById('detailPropID').value = propID || (asset.propID ?? '');
        document.getElementById('detailCategory').value = asset.Category ?? '';
        document.getElementById('detailLokasi').value = asset.Lokasi ?? '';
        document.getElementById('detailProperty').value = asset.Property ?? '';
        document.getElementById('detailMerk').value = asset.Merk ?? '';
        document.getElementById('detailModel').value = asset.Model ?? '';
        document.getElementById('detailSerno').value = asset.serno ?? '';
        document.getElementById('detailCapacity').value = asset.Capacity ?? '';
        document.getElementById('detailDatePurchased').value = asset.DatePurchased ?? '';
        document.getElementById('detailSuplier').value = asset.Suplier ?? '';
        document.getElementById('detailKeterangan').value = asset.Keterangan ?? '';

        // Show image
        const imageName = asset.Gambar ?? '';
        const preview = document.getElementById('imagePreview');
        preview.innerHTML = '';
        if (imageName) {
            const img = document.createElement('img');
            img.src = PHOTO_BASE_URL + imageName + '?t=' + Date.now();
            img.alt = 'Asset Photo';
            preview.appendChild(img);
        } else {
            const span = document.createElement('span');
            span.className = 'muted';
            span.textContent = 'No Image';
            preview.appendChild(span);
        }

        selectedImageFile = null;
        selectedThumbBlob = null;
        document.getElementById('photoInput').value = '';

        document.getElementById('detailSection').style.display = 'block';
    }

    // CLIENT-SIDE IMAGE RESIZE (480px short side) + THUMB 100x100px
    function loadImageToCanvas(file) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = function (e) {
                const img = new Image();
                img.onload = function () {
                    resolve(img);
                };
                img.onerror = function () {
                    reject(new Error('Failed to load image'));
                };
                img.src = e.target.result;
            };
            reader.onerror = function () {
                reject(new Error('Failed to read file'));
            };
            reader.readAsDataURL(file);
        });
    }

    function createResizedBlob(img, targetShortSide = 480, quality = 0.85) {
        const canvas = document.createElement('canvas');
        const w = img.width;
        const h = img.height;
        if (!w || !h) {
            return null;
        }
        let targetW, targetH;
        if (w === h) {
            targetW = targetShortSide;
            targetH = targetShortSide;
        } else {
            const shortSide = Math.min(w, h);
            const scale = targetShortSide / shortSide;
            targetW = Math.round(w * scale);
            targetH = Math.round(h * scale);
        }
        canvas.width = targetW;
        canvas.height = targetH;
        const ctx = canvas.getContext('2d');
        ctx.drawImage(img, 0, 0, targetW, targetH);
        return new Promise((resolve) => {
            canvas.toBlob(function (blob) {
                resolve(blob);
            }, 'image/jpeg', quality);
        });
    }

    function createThumbBlob(img, thumbSize = 100, quality = 0.85) {
        const canvas = document.createElement('canvas');
        const w = img.width;
        const h = img.height;
        if (!w || !h) {
            return null;
        }
        const scale = Math.min(thumbSize / w, thumbSize / h);
        const targetW = Math.round(w * scale);
        const targetH = Math.round(h * scale);
        canvas.width = targetW;
        canvas.height = targetH;
        const ctx = canvas.getContext('2d');
        ctx.drawImage(img, 0, 0, targetW, targetH);
        return new Promise((resolve) => {
            canvas.toBlob(function (blob) {
                resolve(blob);
            }, 'image/jpeg', quality);
        });
    }

    // PREVIEW PHOTO LANGSUNG SAAT DIPILIH
    document.getElementById('photoInput').addEventListener('change', async function () {
        const file = this.files && this.files[0];
        if (!file) {
            return;
        }
        // Preview langsung dari file asli
        const preview = document.getElementById('imagePreview');
        preview.innerHTML = '';
        const img = document.createElement('img');
        img.alt = 'Preview';
        preview.appendChild(img);
        const reader = new FileReader();
        reader.onload = function (e) {
            img.src = e.target.result;
        };
        reader.readAsDataURL(file);

        // Simpan file asli & siapkan blob resize + thumb untuk upload
        selectedImageFile = file;
        selectedThumbBlob = null;

        try {
            const imageObj = await loadImageToCanvas(file);
            const resizedBlob = await createResizedBlob(imageObj, 480, 0.85);
            const thumbBlob = await createThumbBlob(imageObj, 100, 0.85);
            if (resizedBlob) {
                // Replace selectedImageFile dengan versi resize
                selectedImageFile = new File([resizedBlob], file.name, {type: 'image/jpeg'});
            }
            if (thumbBlob) {
                selectedThumbBlob = thumbBlob;
            }
            setStatus('Preview OK. Siap upload photo.', false);
        } catch (e) {
            console.error(e);
            setStatus('Preview OK, tapi gagal resize di client: ' + e.message, true);
        }
    });

    // SAVE DATA (tanpa photo)
    document.getElementById('btnSaveData').addEventListener('click', async function () {
        if (!currentAssetDetail) {
            alert('Pilih asset dulu dari hasil search');
            return;
        }
        const btn = this;
        btn.disabled = true;
        setStatus('Saving data...');

        try {
            const no = document.getElementById('detailNo').value.trim();
            const propID = document.getElementById('detailPropID').value.trim();
            if (!no || !propID) {
                throw new Error('No atau propID kosong');
            }

            const category = document.getElementById('detailCategory').value.trim();
            const lokasi = document.getElementById('detailLokasi').value.trim();
            const property = document.getElementById('detailProperty').value.trim();
            const merk = document.getElementById('detailMerk').value.trim();
            const model = document.getElementById('detailModel').value.trim();
            const serno = document.getElementById('detailSerno').value.trim();
            const capacity = document.getElementById('detailCapacity').value.trim();
            const datePurchased = document.getElementById('detailDatePurchased').value.trim();
            const suplier = document.getElementById('detailSuplier').value.trim();
            const keterangan = document.getElementById('detailKeterangan').value.trim();

            const fd = new FormData();
            fd.append('action', 'update');
            fd.append('no', no);
            fd.append('propID', propID);

            // tgl & mntld diambil dari detail lama jika ada
            const tgl = (currentAssetDetail && currentAssetDetail.tgl) ? currentAssetDetail.tgl : '';
            const mntld = (currentAssetDetail && currentAssetDetail.mntld) ? currentAssetDetail.mntld : '';
            if (tgl) fd.append('tgl', tgl);
            if (mntld) fd.append('mntld', mntld);

            fd.append('Category', category);
            fd.append('Lokasi', lokasi);
            fd.append('Property', property);
            fd.append('Merk', merk);
            fd.append('Model', model);
            fd.append('serno', serno);
            fd.append('Capacity', capacity);
            fd.append('DatePurchased', datePurchased);
            fd.append('Suplier', suplier);
            fd.append('Keterangan', keterangan);

            const resp = await postFormData(fd);
            setStatus('Data saved successfully');
            alert('Data asset berhasil disimpan (tanpa validasi wajib isi)');

            // Refresh detail
            await loadAssetDetail(parseInt(no, 10));
        } catch (e) {
            console.error(e);
            setStatus('Error save data: ' + e.message, true);
            alert('Error save data: ' + e.message);
        } finally {
            btn.disabled = false;
        }
    });

    // UPLOAD PHOTO SAJA
    document.getElementById('btnUploadPhoto').addEventListener('click', async function () {
        if (!currentAssetDetail) {
            alert('Pilih asset dulu dari hasil search');
            return;
        }
        if (!selectedImageFile) {
            alert('Belum pilih photo');
            return;
        }
        const no = document.getElementById('detailNo').value.trim();
        if (!no) {
            alert('No asset tidak ditemukan');
            return;
        }

        const btn = this;
        btn.disabled = true;
        const originalText = btn.textContent;
        btn.textContent = 'Uploading...';
        setStatus('Uploading photo...');

        try {
            const fd = new FormData();
            fd.append('action', 'upload_photo');
            fd.append('no', no);

            // Nama file di server akan di-force menjadi assets_[No].jpg oleh Assets_edit.php
            fd.append('photo', selectedImageFile, selectedImageFile.name || ('assets_' + no + '.jpg'));

            if (selectedThumbBlob) {
                const thumbFileName = 'thumb_assets_' + no + '.jpg';
                fd.append('thumb', selectedThumbBlob, thumbFileName);
            }

            const resp = await postFormData(fd);
            setStatus('Photo uploaded successfully');
            alert('Photo berhasil diupload');

            // Refresh image dari server dengan cache busting
            const imageUrl = PHOTO_BASE_URL + 'assets_' + no + '.jpg?t=' + Date.now();
            const preview = document.getElementById('imagePreview');
            preview.innerHTML = '';
            const img = document.createElement('img');
            img.src = imageUrl;
            preview.appendChild(img);

            // Reset selection setelah upload
            document.getElementById('photoInput').value = '';
            selectedImageFile = null;
            selectedThumbBlob = null;

        } catch (e) {
            console.error(e);
            setStatus('Error upload photo: ' + e.message, true);
            alert('Error upload photo: ' + e.message);
        } finally {
            btn.disabled = false;
            btn.textContent = originalText;
        }
    });
</script>

</body>
</html>


