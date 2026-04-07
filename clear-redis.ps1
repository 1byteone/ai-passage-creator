# 清理 Redis 数据库的 PowerShell 脚本
# 使用 Telnet 或 TCP 连接发送 Redis 命令

$redisHost = "127.0.0.1"
$redisPort = 6379

try {
    # 创建 TCP 连接
    $client = New-Object System.Net.Sockets.TcpClient($redisHost, $redisPort)
    $stream = $client.GetStream()
    $writer = New-Object System.IO.StreamWriter($stream)
    $reader = New-Object System.IO.StreamReader($stream)
    
    # 发送 FLUSHDB 命令 (Redis 协议格式)
    $flushdbCommand = "*1`r`n`$7`r`nFLUSHDB`r`n"
    $writer.Write($flushdbCommand)
    $writer.Flush()
    
    # 读取响应
    Start-Sleep -Milliseconds 100
    $response = $reader.ReadLine()
    
    if ($response -eq "+OK") {
        Write-Host "✓ Redis 数据库已成功清空!" -ForegroundColor Green
    } else {
        Write-Host "✗ 响应: $response" -ForegroundColor Yellow
    }
    
    # 关闭连接
    $client.Close()
} catch {
    Write-Host "✗ 连接 Redis 失败: $_" -ForegroundColor Red
    Write-Host "请尝试以下方法:" -ForegroundColor Cyan
    Write-Host "1. 找到 Redis 安装目录,执行: redis-cli.exe FLUSHDB" -ForegroundColor Cyan
    Write-Host "2. 或者重启 Redis 服务" -ForegroundColor Cyan
}
