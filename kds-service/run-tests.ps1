# KDS Service - Quick Test Runner
# This script helps you quickly run different test suites

Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "   KDS Service Test Suite Runner" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""

$kdsPath = "D:\MYW\UniversityWork\Fullstack\services\kds-service"

function Show-Menu {
    Write-Host "Select test suite to run:" -ForegroundColor Yellow
    Write-Host "1. Run Kafka Producer Tests (5 tests)" -ForegroundColor White
    Write-Host "2. Run Controller Integration Tests (10 tests)" -ForegroundColor White
    Write-Host "3. Run ALL Tests (15 tests)" -ForegroundColor White
    Write-Host "4. Open Mock Frontend in Browser" -ForegroundColor White
    Write-Host "5. Start KDS Service (for manual testing)" -ForegroundColor White
    Write-Host "6. View Test Results Summary" -ForegroundColor White
    Write-Host "0. Exit" -ForegroundColor White
    Write-Host ""
}

function Run-KafkaTests {
    Write-Host "`n[Running Kafka Producer Tests...]" -ForegroundColor Green
    Write-Host "This will test Kafka event publishing with embedded Kafka broker" -ForegroundColor Gray
    Write-Host ""

    Set-Location $kdsPath
    .\mvnw.cmd test -Dtest=KafkaProducerIntegrationTest

    Write-Host "`nKafka tests completed!" -ForegroundColor Green
    Read-Host "`nPress Enter to continue"
}

function Run-ControllerTests {
    Write-Host "`n[Running Controller Integration Tests...]" -ForegroundColor Green
    Write-Host "This will test REST API endpoints" -ForegroundColor Gray
    Write-Host ""

    Set-Location $kdsPath
    .\mvnw.cmd test -Dtest=KitchenControllerIntegrationTest

    Write-Host "`nController tests completed!" -ForegroundColor Green
    Read-Host "`nPress Enter to continue"
}

function Run-AllTests {
    Write-Host "`n[Running ALL Tests...]" -ForegroundColor Green
    Write-Host "This will run the complete test suite" -ForegroundColor Gray
    Write-Host ""

    Set-Location $kdsPath
    .\mvnw.cmd test

    Write-Host "`nAll tests completed!" -ForegroundColor Green
    Read-Host "`nPress Enter to continue"
}

function Open-MockFrontend {
    Write-Host "`n[Opening Mock Frontend...]" -ForegroundColor Green

    $frontendPath = "$kdsPath\src\test\resources\mock-kitchen-frontend.html"

    if (Test-Path $frontendPath) {
        Write-Host "Opening: $frontendPath" -ForegroundColor Gray
        Start-Process $frontendPath
        Write-Host ""
        Write-Host "IMPORTANT: Make sure KDS Service is running!" -ForegroundColor Yellow
        Write-Host "Default URL: http://localhost:8085/api/kitchen" -ForegroundColor Gray
        Write-Host ""
        Write-Host "To start KDS Service, choose option 5 from the menu" -ForegroundColor Cyan
    } else {
        Write-Host "Error: Mock frontend not found at $frontendPath" -ForegroundColor Red
    }

    Read-Host "`nPress Enter to continue"
}

function Start-KDSService {
    Write-Host "`n[Starting KDS Service...]" -ForegroundColor Green
    Write-Host "Service will start on: http://localhost:8085" -ForegroundColor Gray
    Write-Host ""
    Write-Host "PREREQUISITES:" -ForegroundColor Yellow
    Write-Host "1. Order Service must be running on port 8083" -ForegroundColor White
    Write-Host "2. (Optional) Kafka on port 9092 for event publishing" -ForegroundColor White
    Write-Host ""
    Write-Host "Press Ctrl+C to stop the service" -ForegroundColor Cyan
    Write-Host ""

    Set-Location $kdsPath
    .\mvnw.cmd spring-boot:run
}

function Show-TestSummary {
    Write-Host "`n=====================================" -ForegroundColor Cyan
    Write-Host "   Test Suite Summary" -ForegroundColor Cyan
    Write-Host "=====================================" -ForegroundColor Cyan
    Write-Host ""

    Write-Host "1. Kafka Producer Tests (KafkaProducerIntegrationTest)" -ForegroundColor Yellow
    Write-Host "   - testPublishOrderReadyEvent_Success" -ForegroundColor White
    Write-Host "   - testPublishMultipleEvents_AllReceived" -ForegroundColor White
    Write-Host "   - testEventSerialization_AllFieldsPreserved" -ForegroundColor White
    Write-Host "   - testPublishWithEmptyItems_Success" -ForegroundColor White
    Write-Host "   - testMessageKey_MatchesOrderId" -ForegroundColor White
    Write-Host ""

    Write-Host "2. Controller Tests (KitchenControllerIntegrationTest)" -ForegroundColor Yellow
    Write-Host "   - testGetActiveOrders_ReturnsListOfOrders" -ForegroundColor White
    Write-Host "   - testGetActiveOrders_EmptyList" -ForegroundColor White
    Write-Host "   - testMarkOrderReady_Success" -ForegroundColor White
    Write-Host "   - testMarkOrderReady_OrderNotFound" -ForegroundColor White
    Write-Host "   - testMarkOrderReady_ServiceError" -ForegroundColor White
    Write-Host "   - testHealthCheck_ReturnsOk" -ForegroundColor White
    Write-Host "   - testCorsEnabled_AllowsAllOrigins" -ForegroundColor White
    Write-Host "   - testGetActiveOrders_MultipleRequests" -ForegroundColor White
    Write-Host "   - testGetActiveOrders_VerifyResponseStructure" -ForegroundColor White
    Write-Host "   - testMarkOrderReady_DifferentOrderIds" -ForegroundColor White
    Write-Host ""

    Write-Host "3. Mock Frontend (manual testing)" -ForegroundColor Yellow
    Write-Host "   - Interactive UI for testing KDS endpoints" -ForegroundColor White
    Write-Host "   - Real-time order display and updates" -ForegroundColor White
    Write-Host "   - Location: src/test/resources/mock-kitchen-frontend.html" -ForegroundColor White
    Write-Host ""

    Write-Host "Total Automated Tests: 15" -ForegroundColor Green
    Write-Host "Execution Time: ~35 seconds" -ForegroundColor Green
    Write-Host ""

    Read-Host "Press Enter to continue"
}

# Main loop
do {
    Clear-Host
    Write-Host "=====================================" -ForegroundColor Cyan
    Write-Host "   KDS Service Test Suite Runner" -ForegroundColor Cyan
    Write-Host "=====================================" -ForegroundColor Cyan
    Write-Host ""

    Show-Menu
    $choice = Read-Host "Enter your choice (0-6)"

    switch ($choice) {
        "1" { Run-KafkaTests }
        "2" { Run-ControllerTests }
        "3" { Run-AllTests }
        "4" { Open-MockFrontend }
        "5" { Start-KDSService }
        "6" { Show-TestSummary }
        "0" {
            Write-Host "`nExiting... Goodbye!" -ForegroundColor Cyan
            break
        }
        default {
            Write-Host "`nInvalid choice. Please try again." -ForegroundColor Red
            Start-Sleep -Seconds 2
        }
    }
} while ($choice -ne "0")

