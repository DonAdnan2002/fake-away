param(
    [string]$TargetDir = "$PSScriptRoot\..\app\src\main\assets\models"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null

$models = @(
    @{
        Name = "voice_deepfake_wav2vec2.onnx"
        Url  = "https://huggingface.co/ai8shiro/deepfake-audio-wav2vec2-ONNX/resolve/main/onnx/model_q4f16.onnx"
    },
    @{
        Name = "image_ai_smogy.onnx"
        Url  = "https://huggingface.co/onnx-community/SMOGY-Ai-images-detector-ONNX/resolve/main/onnx/model_q4f16.onnx"
    }
)

foreach ($m in $models) {
    $dest = Join-Path $TargetDir $m.Name
    Write-Host "Downloading $($m.Name) from $($m.Url)..."
    Invoke-WebRequest -Uri $m.Url -OutFile $dest
    $sizeMb = [math]::Round((Get-Item $dest).Length / 1MB, 2)
    Write-Host "Saved $dest ($sizeMb MB)"
}
