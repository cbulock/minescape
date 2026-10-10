param(
    [string]$GeneratedBackground = 'C:\Users\camer\.codex\generated_images\01a12799-e171-7b90-9de6-6e1a76b971a9\exec-b935a91e-3e6b-4941-8a14-61f48bace062.png'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$repo = Split-Path -Parent $PSScriptRoot
$output = Join-Path $repo 'store-listing'
$screenshotsOutput = Join-Path $output 'phone-screenshots'
New-Item -ItemType Directory -Force -Path $screenshotsOutput | Out-Null

function Save-Png24 {
    param(
        [System.Drawing.Bitmap]$Bitmap,
        [string]$Path
    )

    $rgb = New-Object System.Drawing.Bitmap($Bitmap.Width, $Bitmap.Height, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $graphics = [System.Drawing.Graphics]::FromImage($rgb)
    try {
        $graphics.DrawImageUnscaled($Bitmap, 0, 0)
        $rgb.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    }
    finally {
        $graphics.Dispose()
        $rgb.Dispose()
    }
}

function Resize-Screenshot {
    param(
        [string]$Source,
        [string]$Destination,
        [int]$TargetWidth = 1080,
        [int]$TargetHeight = 1920
    )

    $image = [System.Drawing.Image]::FromFile($Source)
    $canvas = New-Object System.Drawing.Bitmap($TargetWidth, $TargetHeight, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $graphics = [System.Drawing.Graphics]::FromImage($canvas)
    try {
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $backgroundBrush = New-Object System.Drawing.SolidBrush(([System.Drawing.Bitmap]$image).GetPixel(0, 0))
        $graphics.FillRectangle($backgroundBrush, 0, 0, $TargetWidth, $TargetHeight)
        $backgroundBrush.Dispose()

        $widthScale = [double]$TargetWidth / [double]$image.Width
        $heightScale = [double]$TargetHeight / [double]$image.Height
        $scale = [Math]::Min([double]$widthScale, [double]$heightScale)
        $drawWidth = [int]([Math]::Round($image.Width * $scale))
        $drawHeight = [int]([Math]::Round($image.Height * $scale))
        $drawX = [int](($TargetWidth - $drawWidth) / 2)
        $drawY = [int](($TargetHeight - $drawHeight) / 2)
        $destinationRect = New-Object System.Drawing.Rectangle($drawX, $drawY, $drawWidth, $drawHeight)
        $graphics.DrawImage(
            $image,
            $destinationRect,
            0,
            0,
            $image.Width,
            $image.Height,
            [System.Drawing.GraphicsUnit]::Pixel
        )
        $canvas.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
    }
    finally {
        $graphics.Dispose()
        $canvas.Dispose()
        $image.Dispose()
    }
}

$screenshots = [ordered]@{
    '01-classic-gameplay.png' = 'phone-light.png'
    '02-dark-theme-gameplay.png' = 'phone-dark.png'
    '03-winning-board.png' = 'result-confetti.png'
    '04-practice-mode.png' = 'feature-practice.png'
    '05-daily-challenge.png' = 'daily-phone.png'
    '06-learn-to-play.png' = 'learning-tutorial-phone.png'
    '07-statistics.png' = 'statistics-phone.png'
    '08-choose-your-board.png' = 'phone-home.png'
}

foreach ($item in $screenshots.GetEnumerator()) {
    $source = Join-Path $repo "app/build/outputs/screenshots/$($item.Value)"
    if (-not (Test-Path $source)) {
        throw "Missing native-render screenshot: $source"
    }
    Resize-Screenshot -Source $source -Destination (Join-Path $screenshotsOutput $item.Key)
}

$phoneLandscapeOutput = Join-Path $output 'phone-landscape-screenshots'
New-Item -ItemType Directory -Force -Path $phoneLandscapeOutput | Out-Null
$phoneLandscapeScreenshots = [ordered]@{
    '01-classic-gameplay.png' = 'phone-landscape.png'
    '02-practice-mode.png' = 'feature-practice-loss-landscape.png'
    '03-winning-board.png' = 'result-confetti-landscape.png'
    '04-learn-to-play.png' = 'learning-tutorial-landscape.png'
    '05-result-details.png' = 'result-details-landscape.png'
    '06-settings.png' = 'feature-settings-landscape.png'
}

foreach ($item in $phoneLandscapeScreenshots.GetEnumerator()) {
    $source = Join-Path $repo "app/build/outputs/screenshots/$($item.Value)"
    if (-not (Test-Path $source)) {
        throw "Missing native-render landscape screenshot: $source"
    }
    Resize-Screenshot -Source $source -Destination (Join-Path $phoneLandscapeOutput $item.Key) -TargetWidth 1920 -TargetHeight 1080
}

$tabletPortraitOutput = Join-Path $output 'tablet-screenshots/portrait'
$tabletLandscapeOutput = Join-Path $output 'tablet-screenshots/landscape'
New-Item -ItemType Directory -Force -Path $tabletPortraitOutput | Out-Null
New-Item -ItemType Directory -Force -Path $tabletLandscapeOutput | Out-Null

$tabletPortraitScreenshots = [ordered]@{
    '01-choose-your-board.png' = 'tablet-home.png'
    '02-classic-gameplay.png' = 'tablet-portrait.png'
    '03-settings-and-backup.png' = 'settings-tablet.png'
    '04-daily-challenge-history.png' = 'feature-daily-history-tablet.png'
}

foreach ($item in $tabletPortraitScreenshots.GetEnumerator()) {
    $source = Join-Path $repo "app/build/outputs/screenshots/$($item.Value)"
    if (-not (Test-Path $source)) {
        throw "Missing native-render tablet screenshot: $source"
    }
    Resize-Screenshot -Source $source -Destination (Join-Path $tabletPortraitOutput $item.Key) -TargetWidth 1440 -TargetHeight 2560
}

$tabletLandscapeScreenshots = [ordered]@{
    '01-dark-theme-gameplay.png' = 'tablet-landscape.png'
    '02-settings.png' = 'settings-landscape.png'
    '03-statistics.png' = 'statistics-landscape.png'
    '04-daily-challenge-history.png' = 'daily-landscape-dark.png'
}

foreach ($item in $tabletLandscapeScreenshots.GetEnumerator()) {
    $source = Join-Path $repo "app/build/outputs/screenshots/$($item.Value)"
    if (-not (Test-Path $source)) {
        throw "Missing native-render tablet landscape screenshot: $source"
    }
    Resize-Screenshot -Source $source -Destination (Join-Path $tabletLandscapeOutput $item.Key) -TargetWidth 2560 -TargetHeight 1440
}

$iconSource = Join-Path $repo 'docs/assets/minescape-icon.png'
$iconDestination = Join-Path $output 'app-icon-512.png'
Copy-Item -LiteralPath $iconSource -Destination $iconDestination -Force

if (-not (Test-Path $GeneratedBackground)) {
    throw "Missing generated feature background: $GeneratedBackground"
}

$background = [System.Drawing.Image]::FromFile($GeneratedBackground)
$feature = New-Object System.Drawing.Bitmap(1024, 500, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$featureGraphics = [System.Drawing.Graphics]::FromImage($feature)
try {
    $featureGraphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $featureGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $featureGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $featureGraphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $sourceRatio = $background.Width / $background.Height
    $targetRatio = 1024 / 500
    if ($sourceRatio -gt $targetRatio) {
        $sourceWidth = [int]($background.Height * $targetRatio)
        $sourceX = [int](($background.Width - $sourceWidth) / 2)
        $sourceRect = New-Object System.Drawing.Rectangle($sourceX, 0, $sourceWidth, $background.Height)
    }
    else {
        $sourceHeight = [int]($background.Width / $targetRatio)
        $sourceY = [int](($background.Height - $sourceHeight) / 2)
        $sourceRect = New-Object System.Drawing.Rectangle(0, $sourceY, $background.Width, $sourceHeight)
    }
    $featureGraphics.DrawImage($background, (New-Object System.Drawing.Rectangle(0, 0, 1024, 500)), $sourceRect, [System.Drawing.GraphicsUnit]::Pixel)

    $overlayBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.Point(0, 0)),
        (New-Object System.Drawing.Point(620, 0)),
        [System.Drawing.Color]::FromArgb(125, 40, 38, 64),
        [System.Drawing.Color]::FromArgb(0, 40, 38, 64)
    )
    $featureGraphics.FillRectangle($overlayBrush, 0, 0, 620, 500)
    $overlayBrush.Dispose()

    $font = New-Object System.Drawing.Font('Segoe UI', 78, ([System.Drawing.FontStyle]::Bold), [System.Drawing.GraphicsUnit]::Pixel)
    $textBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
    $featureGraphics.DrawString('Minescape', $font, $textBrush, 64, 188)
    $textBrush.Dispose()
    $font.Dispose()

    $feature.Save((Join-Path $output 'featured-graphic-1024x500.png'), [System.Drawing.Imaging.ImageFormat]::Png)
}
finally {
    $featureGraphics.Dispose()
    $feature.Dispose()
    $background.Dispose()
}

$package = Join-Path $repo 'store-listing-package.zip'
Compress-Archive -Path (Join-Path $output '*') -DestinationPath $package -Force

Write-Output "Generated Google Play assets in $output"
Write-Output "Packaged upload files at $package"
