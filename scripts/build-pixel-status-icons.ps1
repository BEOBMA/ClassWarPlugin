param()
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = Split-Path -Parent $PSScriptRoot
$assetDir = Join-Path $projectRoot 'resource-pack/assets/classwar/textures/status/v4'
$previewDir = Join-Path $projectRoot 'build/icon-previews'
New-Item -ItemType Directory -Path $assetDir, $previewDir -Force | Out-Null

# Explicit 32x32 shapes; no generation model, noise, gradients or random parameters.
function Fill-Shape($canvas, [string]$color, [int[]]$coordinates) {
    $points = [System.Drawing.Point[]]::new($coordinates.Length / 2)
    for ($i = 0; $i -lt $points.Length; $i++) {
        $points[$i] = [System.Drawing.Point]::new($coordinates[$i*2], $coordinates[$i*2+1])
    }
    $brush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($color))
    try { $canvas.FillPolygon($brush, $points) } finally { $brush.Dispose() }
}

foreach ($iconName in @('bleeding', 'burn', 'bullet')) {
    $bitmap = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $canvas = [System.Drawing.Graphics]::FromImage($bitmap)
    $canvas.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
    $canvas.Clear([System.Drawing.Color]::Transparent)
    try {
        switch ($iconName) {
            'bleeding' {
                # Small blood stain, asymmetric lobes; two deliberate negative notches.
                Fill-Shape $canvas '#790F19' @(7,3, 11,3, 14,7, 14,10, 17,8, 20,9, 21,6, 24,6, 26,9, 25,13, 29,15, 29,19, 25,21, 27,25, 24,28, 20,26, 17,29, 12,29, 9,26, 5,27, 3,24, 5,20, 2,18, 2,14, 6,12, 4,8, 5,5)
                Fill-Shape $canvas '#CB2534' @(8,5, 10,5, 12,8, 12,13, 17,11, 21,13, 23,9, 24,10, 23,15, 27,16, 27,18, 23,20, 23,23, 24,25, 21,23, 18,23, 16,27, 13,27, 11,24, 7,24, 5,24, 7,20, 7,17, 4,17, 4,15, 9,14, 8,11, 6,8)
                Fill-Shape $canvas '#EB4241' @(8,6, 10,6, 11,9, 10,11, 8,10, 7,8)
            }
            'burn' {
                # Three large flame tongues, one warm core. No glossy highlight.
                Fill-Shape $canvas '#810E25' @(13,2, 16,5, 16,9, 20,7, 25,12, 27,18, 27,23, 24,28, 19,30, 11,30, 6,27, 3,22, 3,17, 7,11, 7,17, 10,14, 12,10, 11,6)
                Fill-Shape $canvas '#D32C3B' @(13,6, 14,8, 13,12, 11,17, 7,20, 5,19, 5,22, 8,26, 12,28, 18,28, 23,26, 25,22, 25,18, 22,12, 20,10, 20,16, 17,14, 14,13, 15,9)
                Fill-Shape $canvas '#EBAF32' @(16,16, 17,20, 20,18, 21,22, 20,25, 17,27, 13,27, 10,25, 10,22, 13,20)
            }
            'bullet' {
                # A single leaning cartridge, recognizable without texture or extra objects.
                Fill-Shape $canvas '#A8580C' @(21,2, 25,2, 28,5, 28,10, 25,16, 16,29, 12,30, 4,25, 4,22, 13,9, 18,4)
                Fill-Shape $canvas '#E5A724' @(21,4, 24,4, 26,6, 26,10, 23,14, 15,26, 12,27, 7,24, 7,22, 15,10, 19,6)
                Fill-Shape $canvas '#F3CE57' @(21,5, 24,5, 25,7, 25,10, 22,13, 16,9)
                Fill-Shape $canvas '#A8580C' @(14,10, 23,15, 22,17, 13,12)
                Fill-Shape $canvas '#A8580C' @(8,21, 17,26, 16,28, 7,23)
            }
        }
        $bitmap.Save((Join-Path $assetDir "$iconName.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $canvas.Dispose(); $bitmap.Dispose() }
}

# Inspection sheet: nearest-neighbour enlarged pixels plus 32px and 9px display sizes.
$sheet = [System.Drawing.Bitmap]::new(480, 224)
$g = [System.Drawing.Graphics]::FromImage($sheet)
$g.Clear([System.Drawing.Color]::FromArgb(28, 29, 34))
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$font = [System.Drawing.Font]::new('Segoe UI', 11)
try {
    $names = @('bleeding', 'burn', 'bullet')
    for ($index = 0; $index -lt $names.Length; $index++) {
        $source = [System.Drawing.Bitmap]::new((Join-Path $assetDir "$($names[$index]).png"))
        try {
            $g.DrawImage($source, [System.Drawing.Rectangle]::new($index*160+16, 8, 128, 128))
            $g.DrawImage($source, [System.Drawing.Rectangle]::new($index*160+45, 146, 32, 32))
            $g.DrawImage($source, [System.Drawing.Rectangle]::new($index*160+101, 158, 9, 9))
            $g.DrawString($names[$index], $font, [System.Drawing.Brushes]::White, $index*160+42, 191)
        } finally { $source.Dispose() }
    }
    $sheet.Save((Join-Path $previewDir 'status-v4.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally { $font.Dispose(); $g.Dispose(); $sheet.Dispose() }
