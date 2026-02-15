# Frontend Testing Script - Without Kafka
# Tests the KDS service and mock frontend without requiring Kafka

Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "   KDS Frontend Testing (No Kafka)" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""

$kdsPath = "D:\MYW\UniversityWork\Fullstack\services\kds-service"

# Function to test if KDS service is responding
function Test-KDSService {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8085/api/kitchen/health" -UseBasicParsing -ErrorAction SilentlyContinue
        return $response.StatusCode -eq 200
    } catch {
        return $false
    }
}

Write-Host "[Step 1] Testing KDS Service Health Endpoint..." -ForegroundColor Yellow
Write-Host ""

# Check if service is already running
if (Test-KDSService) {
    Write-Host "✅ KDS Service is already running!" -ForegroundColor Green
} else {
    Write-Host "⚠️  KDS Service not detected. Please start it manually:" -ForegroundColor Yellow
    Write-Host "   cd $kdsPath" -ForegroundColor Gray
    Write-Host "   .\mvnw.cmd spring-boot:run" -ForegroundColor Gray
    Write-Host ""

    $start = Read-Host "Would you like me to try starting it? (y/n)"

    if ($start -eq 'y' -or $start -eq 'Y') {
        Write-Host ""
        Write-Host "Starting KDS Service..." -ForegroundColor Yellow
        Write-Host "This will take 10-15 seconds..." -ForegroundColor Gray
        Write-Host ""

        # Start in background
        $process = Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd $kdsPath; .\mvnw.cmd spring-boot:run" -PassThru -WindowStyle Minimized

        Write-Host "Waiting for service to start..." -ForegroundColor Yellow
        $maxWait = 30
        $waited = 0

        while ($waited -lt $maxWait) {
            Start-Sleep -Seconds 2
            $waited += 2
            Write-Host "." -NoNewline

            if (Test-KDSService) {
                Write-Host ""
                Write-Host "✅ KDS Service started successfully!" -ForegroundColor Green
                break
            }
        }

        Write-Host ""

        if (-not (Test-KDSService)) {
            Write-Host "❌ Service didn't start in time. Please check the other window for errors." -ForegroundColor Red
            Write-Host ""
            Write-Host "Common issues:" -ForegroundColor Yellow
            Write-Host "  - Port 8085 already in use" -ForegroundColor Gray
            Write-Host "  - Java not in PATH" -ForegroundColor Gray
            Write-Host "  - Redis connection (set redis.enabled=false in application.yaml)" -ForegroundColor Gray
            exit
        }
    } else {
        Write-Host ""
        Write-Host "Please start the KDS Service manually and run this script again." -ForegroundColor Yellow
        exit
    }
}

Write-Host ""
Write-Host "[Step 2] Testing KDS Endpoints..." -ForegroundColor Yellow
Write-Host ""

# Test Health Endpoint
try {
    $health = Invoke-WebRequest -Uri "http://localhost:8085/api/kitchen/health" -UseBasicParsing
    Write-Host "✅ Health endpoint: $($health.Content)" -ForegroundColor Green
} catch {
    Write-Host "❌ Health endpoint failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Test Orders Endpoint
try {
    $orders = Invoke-WebRequest -Uri "http://localhost:8085/api/kitchen/orders" -UseBasicParsing
    $orderCount = ($orders.Content | ConvertFrom-Json).Count
    Write-Host "✅ Orders endpoint: Returns $orderCount active orders" -ForegroundColor Green
} catch {
    Write-Host "❌ Orders endpoint failed: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "[Step 3] Opening Mock Frontend..." -ForegroundColor Yellow
Write-Host ""

$frontendPath = "$kdsPath\src\test\resources\mock-kitchen-frontend.html"

if (Test-Path $frontendPath) {
    Start-Process $frontendPath
    Write-Host "✅ Mock frontend opened in browser!" -ForegroundColor Green
    Write-Host ""
    Write-Host "=====================================" -ForegroundColor Cyan
    Write-Host "   Frontend is now ready to test!" -ForegroundColor Cyan
    Write-Host "=====================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "What you can test:" -ForegroundColor Yellow
    Write-Host "  ✅ View active orders (currently: $orderCount)" -ForegroundColor White
    Write-Host "  ✅ Auto-refresh functionality" -ForegroundColor White
    Write-Host "  ✅ Activity logs" -ForegroundColor White
    Write-Host "  ✅ Status indicators" -ForegroundColor White
    Write-Host ""
    Write-Host "Note: Without Order Service running, you won't see any orders." -ForegroundColor Gray
    Write-Host "      Without Kafka, 'Mark as READY' will update Order Service but won't publish events." -ForegroundColor Gray
    Write-Host ""
    Write-Host "To test with real orders:" -ForegroundColor Cyan
    Write-Host "  1. Start Order Service: cd ..\order-service; .\mvnw.cmd spring-boot:run" -ForegroundColor Gray
    Write-Host "  2. Create an order using Postman or curl" -ForegroundColor Gray
    Write-Host "  3. Watch it appear in the KDS frontend!" -ForegroundColor Gray
    Write-Host ""
} else {
    Write-Host "❌ Frontend file not found at: $frontendPath" -ForegroundColor Red
}

Write-Host "Press any key to continue..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

