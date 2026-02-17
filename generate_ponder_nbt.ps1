# Generate Ponder schematic .nbt files for motor scenes
# Minecraft Structure Block NBT format, GZip compressed

$ErrorActionPreference = "Stop"

function Write-BEBytes([System.IO.BinaryWriter]$bw, [byte]$tagType) {
    $bw.Write($tagType)
}

function Write-BEShort([System.IO.BinaryWriter]$bw, [int16]$v) {
    $bytes = [BitConverter]::GetBytes($v)
    [Array]::Reverse($bytes)
    $bw.Write($bytes)
}

function Write-BEInt([System.IO.BinaryWriter]$bw, [int32]$v) {
    $bytes = [BitConverter]::GetBytes($v)
    [Array]::Reverse($bytes)
    $bw.Write($bytes)
}

function Write-NBTString([System.IO.BinaryWriter]$bw, [string]$s) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($s)
    $lenBytes = [BitConverter]::GetBytes([uint16]$bytes.Length)
    [Array]::Reverse($lenBytes)
    $bw.Write($lenBytes)
    if ($bytes.Length -gt 0) { $bw.Write($bytes) }
}

function Write-TagHeader([System.IO.BinaryWriter]$bw, [byte]$tagType, [string]$name) {
    $bw.Write($tagType)
    Write-NBTString $bw $name
}

function Write-EndTag([System.IO.BinaryWriter]$bw) {
    $bw.Write([byte]0)
}

function Write-NamedInt([System.IO.BinaryWriter]$bw, [string]$name, [int]$value) {
    Write-TagHeader $bw 3 $name
    Write-BEInt $bw $value
}

function Write-NamedString([System.IO.BinaryWriter]$bw, [string]$name, [string]$value) {
    Write-TagHeader $bw 8 $name
    Write-NBTString $bw $value
}

function Write-Structure {
    param(
        [string]$OutputPath,
        [array]$Palette,
        [array]$Blocks,
        [int]$SizeX,
        [int]$SizeY,
        [int]$SizeZ
    )

    $ms = New-Object System.IO.MemoryStream
    $bw = New-Object System.IO.BinaryWriter($ms)

    # Root compound (TAG_Compound with empty name)
    $bw.Write([byte]10)
    Write-NBTString $bw ""

    # DataVersion
    Write-NamedInt $bw "DataVersion" 3955

    # size: TAG_List of TAG_Int (3 elements)
    Write-TagHeader $bw 9 "size"
    $bw.Write([byte]3)  # element type = TAG_Int
    Write-BEInt $bw 3   # count
    Write-BEInt $bw $SizeX
    Write-BEInt $bw $SizeY
    Write-BEInt $bw $SizeZ

    # palette: TAG_List of TAG_Compound
    Write-TagHeader $bw 9 "palette"
    $bw.Write([byte]10)  # element type = TAG_Compound
    Write-BEInt $bw $Palette.Count
    foreach ($entry in $Palette) {
        Write-NamedString $bw "Name" $entry.Name
        if ($entry.Props -and $entry.Props.Count -gt 0) {
            Write-TagHeader $bw 10 "Properties"
            foreach ($key in $entry.Props.Keys) {
                Write-NamedString $bw $key $entry.Props[$key]
            }
            Write-EndTag $bw  # end Properties compound
        }
        Write-EndTag $bw  # end palette entry
    }

    # blocks: TAG_List of TAG_Compound
    Write-TagHeader $bw 9 "blocks"
    $bw.Write([byte]10)  # element type = TAG_Compound
    Write-BEInt $bw $Blocks.Count
    foreach ($block in $Blocks) {
        # pos: TAG_List of TAG_Int
        Write-TagHeader $bw 9 "pos"
        $bw.Write([byte]3)  # TAG_Int
        Write-BEInt $bw 3   # count = 3
        Write-BEInt $bw $block.X
        Write-BEInt $bw $block.Y
        Write-BEInt $bw $block.Z
        # state: TAG_Int
        Write-NamedInt $bw "state" $block.State
        Write-EndTag $bw  # end block compound
    }

    # entities: empty TAG_List of TAG_Compound
    Write-TagHeader $bw 9 "entities"
    $bw.Write([byte]10)  # TAG_Compound
    Write-BEInt $bw 0    # empty

    Write-EndTag $bw  # end root compound

    $bw.Flush()
    $rawBytes = $ms.ToArray()
    $bw.Close()
    $ms.Close()

    # Create directory
    $dir = Split-Path $OutputPath
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }

    # GZip compress and write
    $fs = [System.IO.File]::Create($OutputPath)
    $gz = New-Object System.IO.Compression.GZipStream($fs, [System.IO.Compression.CompressionMode]::Compress)
    $gz.Write($rawBytes, 0, $rawBytes.Length)
    $gz.Close()
    $fs.Close()

    $compressed = (Get-Item $OutputPath).Length
    Write-Host "Written: $OutputPath ($($rawBytes.Length) raw, $compressed compressed)"
}

# ============================================================
# Helper: generate floor blocks (y=0, full 7x7 grid)
# ============================================================
function Get-FloorBlocks([int]$stateIndex) {
    $blocks = @()
    for ($x = 0; $x -lt 7; $x++) {
        for ($z = 0; $z -lt 7; $z++) {
            $blocks += @{ X = $x; Y = 0; Z = $z; State = $stateIndex }
        }
    }
    return $blocks
}

$basePath = "F:\Controller\CreateLogicLink\src\main\resources\assets\logiclink\ponder"

# ============================================================
# Creative Logic Motor Scene
# Layout: Motor at (3,1,3) facing west, computer at (4,1,3),
#         shafts at (2,1,3), (1,1,3), (0,1,3)
# ============================================================

$creativePalette = @(
    @{ Name = "minecraft:air"; Props = @{} },
    @{ Name = "minecraft:smooth_stone"; Props = @{} },
    @{ Name = "logiclink:creative_logic_motor"; Props = @{ facing = "west" } },
    @{ Name = "computercraft:computer_normal"; Props = @{ facing = "west"; state = "off" } },
    @{ Name = "create:shaft"; Props = @{ axis = "x"; waterlogged = "false" } }
)

$creativeBlocks = Get-FloorBlocks 1  # Floor = smooth_stone (index 1)
$creativeBlocks += @(
    @{ X = 3; Y = 1; Z = 3; State = 2 },  # creative_logic_motor
    @{ X = 4; Y = 1; Z = 3; State = 3 },  # computer
    @{ X = 2; Y = 1; Z = 3; State = 4 },  # shaft
    @{ X = 1; Y = 1; Z = 3; State = 4 },  # shaft
    @{ X = 0; Y = 1; Z = 3; State = 4 }   # shaft
)

Write-Structure `
    -OutputPath "$basePath\creative_logic_motor\overview.nbt" `
    -Palette $creativePalette `
    -Blocks $creativeBlocks `
    -SizeX 7 -SizeY 4 -SizeZ 7

# ============================================================
# Logic Motor Scene
# Layout: Motor at (3,1,3) facing east, computer at (3,1,4),
#         input shafts at (1,1,3), (0,1,3)
#         output shafts at (5,1,3), (6,1,3)
# ============================================================

$logicPalette = @(
    @{ Name = "minecraft:air"; Props = @{} },
    @{ Name = "minecraft:smooth_stone"; Props = @{} },
    @{ Name = "logiclink:logic_motor"; Props = @{ facing = "east"; active = "false" } },
    @{ Name = "computercraft:computer_normal"; Props = @{ facing = "north"; state = "off" } },
    @{ Name = "create:shaft"; Props = @{ axis = "x"; waterlogged = "false" } }
)

$logicBlocks = Get-FloorBlocks 1  # Floor = smooth_stone
$logicBlocks += @(
    @{ X = 3; Y = 1; Z = 3; State = 2 },  # logic_motor
    @{ X = 3; Y = 1; Z = 4; State = 3 },  # computer
    @{ X = 2; Y = 1; Z = 3; State = 4 },  # shaft (input side)
    @{ X = 1; Y = 1; Z = 3; State = 4 },  # shaft (input side)
    @{ X = 0; Y = 1; Z = 3; State = 4 },  # shaft (input side)
    @{ X = 4; Y = 1; Z = 3; State = 4 },  # shaft (output side)
    @{ X = 5; Y = 1; Z = 3; State = 4 },  # shaft (output side)
    @{ X = 6; Y = 1; Z = 3; State = 4 }   # shaft (output side)
)

Write-Structure `
    -OutputPath "$basePath\logic_motor\overview.nbt" `
    -Palette $logicPalette `
    -Blocks $logicBlocks `
    -SizeX 7 -SizeY 4 -SizeZ 7

# ============================================================
# Contraption Remote Scene
# Layout: Contraption Remote at (3,1,3) facing south,
#         Create seat at (3,1,4),
#         Redstone Link at (1,1,3), Lamp at (1,1,1),
#         Logic Drive at (5,1,3), Shaft at (6,1,3)
# ============================================================

$contraptionPalette = @(
    @{ Name = "minecraft:air"; Props = @{} },
    @{ Name = "minecraft:smooth_stone"; Props = @{} },
    @{ Name = "logiclink:contraption_remote"; Props = @{ facing = "south" } },
    @{ Name = "create:seat"; Props = @{ color = "brown" } },
    @{ Name = "create:redstone_link"; Props = @{ facing = "up"; powered = "false" } },
    @{ Name = "minecraft:redstone_lamp"; Props = @{ lit = "false" } },
    @{ Name = "logiclink:logic_drive"; Props = @{ facing = "east"; active = "false" } },
    @{ Name = "create:shaft"; Props = @{ axis = "x"; waterlogged = "false" } }
)

$contraptionBlocks = Get-FloorBlocks 1  # Floor
$contraptionBlocks += @(
    @{ X = 3; Y = 1; Z = 3; State = 2 },  # contraption_remote
    @{ X = 3; Y = 1; Z = 4; State = 3 },  # seat
    @{ X = 1; Y = 1; Z = 3; State = 4 },  # redstone_link
    @{ X = 1; Y = 1; Z = 1; State = 5 },  # redstone_lamp
    @{ X = 5; Y = 1; Z = 3; State = 6 },  # logic_drive
    @{ X = 6; Y = 1; Z = 3; State = 7 }   # shaft
)

Write-Structure `
    -OutputPath "$basePath\contraption_remote\overview.nbt" `
    -Palette $contraptionPalette `
    -Blocks $contraptionBlocks `
    -SizeX 7 -SizeY 4 -SizeZ 7

Write-Host "`nDone! All ponder schematics generated."
