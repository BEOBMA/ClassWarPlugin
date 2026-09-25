Add-Type -AssemblyName System.Drawing

$root = Join-Path $PSScriptRoot "..\resource-pack\assets\classwar\textures\status"
$root = [System.IO.Path]::GetFullPath($root)
$icons = @{
    Settlement = @("#D9563D", "#FFB16A", "settlement")
    Aftermath = @("#D99D22", "#FFE27A", "aftermath")
    Resonance = @("#A255D0", "#E1A7FF", "resonance")
    VariableRhythm = @("#28AAC4", "#A0F2FF", "music")
    Writing = @("#D5A637", "#FFF0A6", "quill")
    Caduceus = @("#D89B26", "#FFE17B", "caduceus")
    Directive = @("#D8AC28", "#FFF09A", "directive")
    AgentDamageDealt = @("#D7463C", "#FF9A6E", "damageup")
    AgentDamageTaken = @("#4B91B0", "#B8EFFF", "damagedown")
    AgentHatchet = @("#C87836", "#F4C274", "hatchet")
    AgentStiletto = @("#53B8C7", "#C6F8FF", "stiletto")
    AgentBastard = @("#D19A2B", "#FFE07A", "bastardsword")
    AgentRapier = @("#36AFC6", "#C3F4FF", "rapier")
    AgentHammer = @("#A97743", "#E2C28C", "hammer")
    AgentGreatsword = @("#D9543F", "#FFAE7B", "greatsword")
    AgentLance = @("#388CAF", "#A8E8FF", "lance")
    AgentWhip = @("#9560C3", "#DBB4FF", "whip")
    AgentScythe = @("#7653B6", "#C9A8FF", "scythe")
    Arrow = @("#D5A32A", "#FFE27B", "arrow")
    Invalidity = @("#747986", "#D4D7DE", "slash")
    AbnormalStatusDamage = @("#E3263A", "#FF6B55", "burst")
    Gravity = @("#8A42DC", "#D5A4FF", "orbit")
    Card = @("#F0B820", "#FFF1A6", "card")
    Untargetability = @("#84909C", "#DAE1E8", "eyeoff")
    Abyss = @("#7136B6", "#B978FF", "abyss")
    Silence = @("#626875", "#C8D0DB", "mute")
    Disarm = @("#C84939", "#FF9A70", "disarm")
    RespiteHealth = @("#E52C4D", "#FF8A9A", "heart")
    Execution = @("#C82837", "#FF6675", "blade")
    Electrocution = @("#F2C51D", "#FFF57B", "bolt")
    Stun = @("#F0A821", "#FFE26C", "stars")
    Snare = @("#72A83A", "#BFE875", "snare")
    Brightness = @("#F0C52D", "#FFF3A1", "sun")
    Radiation = @("#91B82C", "#D7F15D", "radiation")
    Enchantment = @("#8552C3", "#D4A7FF", "book")
    Charge = @("#E6A92A", "#FFE06A", "charge")
    Fix = @("#319D9A", "#8CE3D3", "anchor")
    Frostbite = @("#28AFCB", "#A5F0FF", "snow")
    Freezing = @("#55BFD8", "#E4FBFF", "ice")
    DimensionMarker = @("#7655D6", "#C7A8FF", "diamond")
    Erosion = @("#A17A36", "#E3C37D", "erosion")
    FreikugelBullet = @("#C17B29", "#FFD274", "bulletstar")
    AccelerationBullet = @("#E66C26", "#FFD166", "speedbullet")
    Foresight = @("#19A9AD", "#9CF3EC", "eye")
    Acceleration = @("#E78D24", "#FFE078", "speed")
    Disposal = @("#D4383D", "#FF7773", "combo")
    PhotographyStack = @("#557DB5", "#B6D3FF", "camera")
    Checkpoint = @("#209A8D", "#8DE3C9", "flag")
    TimePhase = @("#D3A322", "#FFE276", "clock")
    Area = @("#BA8540", "#E7BE69", "ring")
    Distortion = @("#8863B7", "#D5B8FF", "spiral")
    Disability = @("#858A96", "#D1D4DB", "broken")
    Invincibility = @("#389FC1", "#C6F5FF", "shieldstar")
}

function ColorOf([string]$hex) {
    return [System.Drawing.ColorTranslator]::FromHtml($hex)
}
function FillPoly($g, $brush, [int[]]$coords) {
    $points = [System.Collections.Generic.List[System.Drawing.Point]]::new()
    for ($i = 0; $i -lt $coords.Length; $i += 2) {
        $points.Add([System.Drawing.Point]::new($coords[$i], $coords[$i + 1]))
    }
    $g.FillPolygon($brush, $points.ToArray())
}
function DrawIcon($g, $dark, $light, [string]$shape) {
    $pen = [System.Drawing.Pen]::new($dark, 3.2)
    $lightBrush = [System.Drawing.SolidBrush]::new($light)
    $darkBrush = [System.Drawing.SolidBrush]::new($dark)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    switch ($shape) {
        "settlement" { FillPoly $g $darkBrush @(4,14,9,8,14,12,19,5,24,10,29,8,25,18,20,15,15,23,10,18,5,22); $g.DrawLine($pen,5,27,27,27); $g.FillEllipse($lightBrush,7,4,4,4); $g.FillEllipse($lightBrush,24,3,4,4); $g.FillEllipse($lightBrush,27,22,3,3) }
        "aftermath" { $g.DrawArc($pen,3,5,26,22,195,150); $g.DrawArc([System.Drawing.Pen]::new($light,2.4),7,9,18,15,198,145); FillPoly $g $darkBrush @(14,3,18,3,20,12,28,15,20,18,17,28,14,20,5,17,13,13); FillPoly $g $lightBrush @(16,8,17,13,22,15,17,16,16,21,15,16,11,15,15,13) }
        "resonance" { $g.DrawArc($pen,2,5,16,22,285,150); $g.DrawArc($pen,14,5,16,22,105,150); $g.DrawArc([System.Drawing.Pen]::new($light,2.2),7,9,10,14,285,150); $g.DrawArc([System.Drawing.Pen]::new($light,2.2),15,9,10,14,105,150); FillPoly $g $darkBrush @(16,7,21,16,16,25,11,16); FillPoly $g $lightBrush @(16,12,18,16,16,20,14,16) }
        "music" { $g.FillEllipse($darkBrush,5,20,9,7); $g.DrawLine($pen,12,22,12,5); FillPoly $g $darkBrush @(12,5,25,2,25,7,12,10); $g.DrawArc($pen,16,10,12,12,300,120); $g.DrawArc($pen,18,8,13,16,300,120) }
        "quill" { FillPoly $g $darkBrush @(25,2,29,5,14,24,8,29,10,21); FillPoly $g $lightBrush @(24,6,26,7,14,21,12,20); $g.DrawLine($pen,8,29,3,30); $g.DrawLine($pen,14,17,22,8) }
        "caduceus" { $g.DrawLine($pen,16,8,16,29); FillPoly $g $darkBrush @(16,8,5,3,8,10,16,13,24,10,27,3); $g.DrawArc($pen,9,10,13,11,0,180); $g.DrawArc($pen,10,16,13,11,180,180); $g.FillEllipse($lightBrush,13,5,6,6) }
        "directive" { FillPoly $g $darkBrush @(6,3,22,3,27,8,27,29,6,29); FillPoly $g $lightBrush @(9,6,21,6,24,9,24,26,9,26); $g.DrawLine($pen,12,13,21,13); $g.DrawLine($pen,12,18,16,22); $g.DrawLine($pen,16,22,22,16) }
        "damageup" { FillPoly $g $darkBrush @(20,2,27,5,14,23,8,28,10,20); FillPoly $g $lightBrush @(20,6,22,7,13,20,11,21); $g.DrawLine($pen,5,27,15,17); FillPoly $g $lightBrush @(24,12,28,12,28,8,31,14,28,20,28,16,24,16) }
        "damagedown" { FillPoly $g $darkBrush @(16,2,28,7,26,20,16,30,6,20,4,7); FillPoly $g $lightBrush @(16,6,24,9,22,19,16,25,10,19,8,9); FillPoly $g $darkBrush @(14,11,18,11,18,18,22,18,16,25,10,18,14,18) }
        "hatchet" { $g.DrawLine($pen,8,28,22,7); FillPoly $g $darkBrush @(18,3,28,4,30,11,26,17,19,13,16,10); FillPoly $g $lightBrush @(20,6,25,7,26,10,24,13,20,11) }
        "stiletto" { FillPoly $g $darkBrush @(16,2,21,17,16,25,11,17); FillPoly $g $lightBrush @(16,7,18,17,16,20,14,17); $g.DrawLine($pen,8,16,24,16); $g.FillRectangle($darkBrush,14,23,4,6); $g.FillEllipse($lightBrush,14,28,4,3) }
        "bastardsword" { FillPoly $g $darkBrush @(13,2,20,2,22,20,16,25,10,20); FillPoly $g $lightBrush @(16,6,18,6,19,19,16,21,13,19); $g.DrawLine($pen,6,18,26,18); $g.FillRectangle($darkBrush,14,23,4,6); $g.FillEllipse($lightBrush,14,28,4,3) }
        "rapier" { FillPoly $g $darkBrush @(15,2,18,2,19,22,16,26,13,22); FillPoly $g $lightBrush @(16,6,17,6,17,21,16,22,15,21); $g.DrawArc($pen,4,17,24,9,0,180); $g.FillRectangle($darkBrush,14,24,4,5); $g.FillEllipse($lightBrush,14,28,4,3) }
        "hammer" { $g.DrawLine($pen,13,14,20,29); FillPoly $g $darkBrush @(4,3,26,3,29,8,26,15,5,15,2,9); FillPoly $g $lightBrush @(7,6,23,6,25,9,23,12,7,12,5,9) }
        "greatsword" { FillPoly $g $darkBrush @(11,2,21,2,25,17,16,25,7,17); FillPoly $g $lightBrush @(15,6,18,6,21,17,16,21,11,17); $g.DrawLine($pen,4,17,28,17); $g.FillRectangle($darkBrush,14,23,4,6); $g.FillEllipse($lightBrush,14,28,4,3) }
        "lance" { $g.DrawLine($pen,8,29,23,4); FillPoly $g $darkBrush @(18,2,29,1,24,13,19,12,16,8); FillPoly $g $lightBrush @(21,5,25,4,22,10,20,9); $g.DrawLine($pen,6,25,12,29) }
        "whip" { $g.DrawLine($pen,5,29,10,19); $g.DrawLine($pen,10,19,8,13); $g.DrawLine($pen,8,13,14,9); $g.DrawLine($pen,14,9,22,12); $g.DrawLine($pen,22,12,27,6); $g.DrawLine([System.Drawing.Pen]::new($light,2),6,28,11,19); $g.FillEllipse($darkBrush,2,27,6,4) }
        "scythe" { $g.DrawLine($pen,7,29,19,8); $g.DrawArc($pen,4,1,25,18,195,145); FillPoly $g $darkBrush @(13,9,28,3,24,10,15,14); FillPoly $g $lightBrush @(17,10,24,7,22,10) }
        "arrow" { $g.DrawLine($pen,5,27,24,8); FillPoly $g $darkBrush @(16,4,29,2,27,15,23,11); FillPoly $g $lightBrush @(21,7,25,6,24,10); FillPoly $g $darkBrush @(4,27,10,22,11,28,5,30); FillPoly $g $darkBrush @(8,30,13,24,14,30) }
        "slash" { FillPoly $g $darkBrush @(8,5,14,5,24,23,18,27); FillPoly $g $lightBrush @(10,7,13,7,22,23,19,24) }
        "burst" { FillPoly $g $darkBrush @(15,2,19,10,29,7,23,16,29,23,18,21,13,30,11,20,3,18,11,13,8,5); FillPoly $g $lightBrush @(15,8,18,14,24,12,20,17,24,20,17,18,14,24,13,17,8,16,14,13) }
        "orbit" { $g.DrawEllipse($pen,5,5,22,22); FillPoly $g $lightBrush @(13,1,17,1,18,8,14,8); $g.FillEllipse($lightBrush,18,18,5,5); $g.FillEllipse($darkBrush,8,8,5,5) }
        "card" { $g.FillRectangle($darkBrush,6,3,20,26); $g.FillRectangle($lightBrush,9,6,14,20); FillPoly $g $darkBrush @(16,8,19,14,16,20,13,14); $g.FillRectangle($darkBrush,14,23,4,2) }
        "eyeoff" { FillPoly $g $darkBrush @(2,15,8,8,15,5,23,9,29,15,23,22,15,25,8,21); $g.FillEllipse($lightBrush,12,11,7,8); $g.FillEllipse($darkBrush,14,13,3,4); $g.DrawLine($pen,5,27,27,5) }
        "abyss" { $g.FillEllipse($darkBrush,3,3,26,26); $g.FillEllipse($lightBrush,9,9,14,14); $g.FillEllipse($darkBrush,13,13,7,7) }
        "mute" { FillPoly $g $darkBrush @(3,12,9,12,16,6,16,26,9,20,3,20); $g.DrawArc($pen,12,7,14,18,300,120); $g.DrawLine($pen,22,10,28,22); $g.DrawLine($pen,28,10,22,22) }
        "disarm" { FillPoly $g $darkBrush @(3,7,8,3,16,11,24,3,29,8,21,16,28,23,23,28,15,20,7,28,2,23,10,15); FillPoly $g $lightBrush @(6,8,8,6,13,11,11,13) }
        "heart" { FillPoly $g $darkBrush @(16,28,3,16,3,9,7,5,13,5,16,9,19,5,25,5,29,9,29,16); FillPoly $g $lightBrush @(16,24,7,15,7,10,10,8,14,10,16,14,19,10,23,8,26,10,26,15) }
        "blade" { FillPoly $g $darkBrush @(23,2,28,5,12,23,7,18); FillPoly $g $lightBrush @(23,6,24,7,12,20,10,18); $g.FillRectangle($darkBrush,5,19,4,8); $g.DrawLine($pen,3,28,12,19) }
        "bolt" { FillPoly $g $darkBrush @(17,1,6,17,13,17,10,30,26,12,18,12); FillPoly $g $lightBrush @(16,7,10,15,16,15,14,23,22,13,16,13) }
        "stars" { $g.FillEllipse($darkBrush,12,9,8,8); FillPoly $g $lightBrush @(16,1,18,10,25,6,20,13,30,16,20,18,25,26,17,21,14,30,13,20,4,24,10,17,1,13,12,13) }
        "snare" { $g.DrawEllipse($pen,5,5,22,22); $g.DrawArc($pen,10,9,12,12,180,250); FillPoly $g $lightBrush @(13,13,19,13,16,20) }
        "sun" { $g.FillEllipse($darkBrush,9,9,14,14); $g.FillEllipse($lightBrush,12,12,8,8); foreach($a in 0..7){$t=$a*[Math]::PI/4;$x1=16+[Math]::Round(11*[Math]::Cos($t));$y1=16+[Math]::Round(11*[Math]::Sin($t));$x2=16+[Math]::Round(14*[Math]::Cos($t));$y2=16+[Math]::Round(14*[Math]::Sin($t));$g.DrawLine($pen,$x1,$y1,$x2,$y2)} }
        "radiation" { $g.FillEllipse($darkBrush,5,5,22,22); $g.FillEllipse($lightBrush,13,13,6,6); foreach($a in @(0,120,240)){ $g.FillPie($lightBrush,8,8,16,16,$a,45) }; $g.FillEllipse($darkBrush,14,14,4,4) }
        "book" { FillPoly $g $darkBrush @(3,7,14,9,16,12,18,9,29,7,29,25,18,27,16,30,14,27,3,25); $g.DrawLine($pen,16,12,16,26); $g.FillRectangle($lightBrush,6,12,6,2); $g.FillRectangle($lightBrush,20,12,6,2) }
        "charge" { FillPoly $g $darkBrush @(4,18,10,18,10,12,16,12,16,6,22,6,22,12,28,12,28,18,22,18,22,24,16,24,16,29,10,29,10,24,4,24); $g.FillRectangle($lightBrush,12,14,9,9) }
        "anchor" { $g.DrawLine($pen,16,4,16,22); $g.DrawEllipse($pen,12,2,8,8); $g.DrawLine($pen,6,13,26,13); $g.DrawArc($pen,4,13,24,16,0,180); FillPoly $g $lightBrush @(4,23,9,24,16,19,23,24,28,23,23,29,16,26,9,29) }
        "snow" { foreach($a in @(0,60,120)){ $t=$a*[Math]::PI/180;$dx=[Math]::Round(12*[Math]::Cos($t));$dy=[Math]::Round(12*[Math]::Sin($t));$g.DrawLine($pen,16-$dx,16-$dy,16+$dx,16+$dy) }; $g.FillEllipse($lightBrush,13,13,6,6) }
        "ice" { FillPoly $g $darkBrush @(5,8,16,2,27,8,27,23,16,30,5,23); FillPoly $g $lightBrush @(8,10,16,5,23,9,23,21,16,26,8,21); $g.DrawLine($pen,16,5,16,26); $g.DrawLine($pen,8,10,23,21) }
        "diamond" { FillPoly $g $darkBrush @(16,2,29,14,16,30,3,14); FillPoly $g $lightBrush @(16,6,24,14,16,25,8,14); $g.DrawLine($pen,16,6,16,25) }
        "erosion" { FillPoly $g $darkBrush @(5,4,27,4,24,11,20,11,17,17,13,17,10,24,4,27,7,19,3,16,8,12); $g.FillEllipse($lightBrush,20,20,5,5); $g.FillEllipse($lightBrush,11,24,3,3) }
        "bulletstar" { FillPoly $g $darkBrush @(14,2,20,2,25,15,22,24,16,30,10,24,7,15); FillPoly $g $lightBrush @(15,5,19,5,22,15,20,22,16,26,12,22,10,15); FillPoly $g $darkBrush @(16,8,18,13,23,13,19,16,21,21,16,18,12,21,13,16,9,13,14,13) }
        "speedbullet" { FillPoly $g $darkBrush @(18,2,25,5,24,20,17,29,10,20,10,8); FillPoly $g $lightBrush @(18,6,21,8,20,18,17,23,14,18,14,9); $g.DrawLine($pen,2,10,8,10); $g.DrawLine($pen,1,16,7,16); $g.DrawLine($pen,3,22,8,22) }
        "eye" { FillPoly $g $darkBrush @(2,16,8,8,15,5,23,8,30,16,23,24,15,27,8,23); $g.FillEllipse($lightBrush,11,11,10,10); $g.FillEllipse($darkBrush,14,14,4,4) }
        "speed" { FillPoly $g $darkBrush @(18,2,28,2,17,14,25,14,8,30,12,18,5,18); FillPoly $g $lightBrush @(18,8,22,8,13,17,18,17,12,24,15,15,11,15) }
        "combo" { FillPoly $g $darkBrush @(4,4,12,4,16,11,20,4,28,4,24,14,29,21,21,28,14,23,7,28,2,20,8,14); FillPoly $g $lightBrush @(10,8,13,8,17,16,21,8,24,8,20,16,24,21,20,24,15,19,10,24,7,20,12,15) }
        "camera" { $g.FillRectangle($darkBrush,3,9,26,17); $g.FillRectangle($lightBrush,8,6,8,4); $g.FillEllipse($lightBrush,10,11,12,12); $g.FillEllipse($darkBrush,13,14,6,6); $g.FillRectangle($lightBrush,23,11,3,3) }
        "flag" { $g.DrawLine($pen,8,3,8,29); FillPoly $g $darkBrush @(9,5,27,5,23,12,27,19,9,19); FillPoly $g $lightBrush @(11,8,22,8,19,12,22,16,11,16) }
        "clock" { $g.FillEllipse($darkBrush,3,3,26,26); $g.FillEllipse($lightBrush,7,7,18,18); $g.DrawLine($pen,16,9,16,16); $g.DrawLine($pen,16,16,22,19); $g.FillEllipse($darkBrush,14,14,4,4) }
        "ring" { $g.DrawEllipse($pen,4,4,24,24); $g.DrawEllipse([System.Drawing.Pen]::new($light,2),9,9,14,14); $g.FillEllipse($lightBrush,14,14,4,4) }
        "spiral" { $g.DrawArc($pen,4,4,24,24,20,290); $g.DrawArc([System.Drawing.Pen]::new($light,2),10,10,12,12,190,290); $g.FillEllipse($darkBrush,14,14,4,4) }
        "broken" { FillPoly $g $darkBrush @(6,3,13,3,15,12,18,12,20,3,27,3,25,14,20,19,21,28,14,28,13,21,9,20,5,14); $g.DrawLine([System.Drawing.Pen]::new($light,2),12,4,15,11); $g.DrawLine([System.Drawing.Pen]::new($light,2),20,4,18,11) }
        "shieldstar" { FillPoly $g $darkBrush @(16,2,28,7,26,20,16,30,6,20,4,7); FillPoly $g $lightBrush @(16,6,24,9,22,19,16,25,10,19,8,9); FillPoly $g $darkBrush @(16,9,18,14,24,14,19,18,21,23,16,20,11,23,13,18,8,14,14,14) }
    }
    $pen.Dispose(); $lightBrush.Dispose(); $darkBrush.Dispose()
}

foreach ($entry in $icons.GetEnumerator()) {
    $bitmap = [System.Drawing.Bitmap]::new(1254, 1254, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.ScaleTransform(1254.0 / 32.0, 1254.0 / 32.0)
        DrawIcon $graphics (ColorOf $entry.Value[0]) (ColorOf $entry.Value[1]) $entry.Value[2]
        $bitmap.Save((Join-Path $root ($entry.Key.ToLowerInvariant() + ".png")), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $graphics.Dispose(); $bitmap.Dispose() }
}
Write-Output "Generated $($icons.Count) 1254x1254 transparent draft-style status icons in $root"
