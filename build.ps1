# Build script for Halma project
Param()

Write-Host "Compiling Java sources..."
Push-Location (Split-Path -Parent $MyInvocation.MyCommand.Definition)

# Attempt to stop any running Halma server instances before building.
Write-Host "Checking for running Halma server instances (port 9090 / HalmaServer) ..."
try {
	# Find processes listening on TCP 9090 (Windows 10+): use Get-NetTCPConnection if available
	$listeners = @()
	if (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue) {
		$listeners = Get-NetTCPConnection -LocalPort 9090 -State Listen -ErrorAction SilentlyContinue
		} else {
		# Fallback: use netstat and parse
		$net = netstat -ano | Select-String ":9090"
		foreach ($l in $net) {
			$parts = ($l -split '\s+')
			$pid = $parts[-1]
			if ($pid) { $listeners += @{OwningProcess=$pid} }
		}
	}

	$pids = @()
	foreach ($l in $listeners) {
		if ($l.OwningProcess) { $pids += [int]$l.OwningProcess }
	}

	# Also check for java processes whose command line mentions HalmaServer
	$javaProcs = Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" | Where-Object { $_.CommandLine -match 'HalmaServer' }
	foreach ($jp in $javaProcs) { $pids += $jp.ProcessId }

	$pids = $pids | Select-Object -Unique
	if ($pids.Count -gt 0) {
		Write-Host "Found running server PIDs: $($pids -join ', ') - attempting to stop..."
		foreach ($targetPid in $pids) {
			try {
				Stop-Process -Id $targetPid -Force -ErrorAction Stop
				Write-Host "Stopped process $targetPid"
			} catch {
				Write-Warning "Failed to stop ${targetPid}: $_"
			}
		}
		Start-Sleep -Seconds 1
	} else { Write-Host "No running Halma server instances detected." }
} catch {
	Write-Warning "Error while attempting to detect/stop server processes: $_"
}

$src = Get-ChildItem -Recurse -Filter *.java | Where-Object { $_.FullName -notmatch 'root_backup_OLD' } | ForEach-Object { $_.FullName }
if ($src.Count -eq 0) { Write-Error "No .java files found"; exit 1 }

javac -d . $src
if ($LASTEXITCODE -ne 0) { Write-Error "Compilation failed"; Pop-Location; exit 1 }

Write-Host "Creating halma.jar..."
if (Test-Path halma.jar) { Remove-Item halma.jar }

# Create a temporary manifest file
$manifest = @"
Main-Class: halma.Launcher

"@
$manifestPath = Join-Path $PWD "manifest.txt"
$manifest | Out-File -FilePath $manifestPath -Encoding ASCII

# If open-firewall.ps1 exists, include it at the root of the jar so the server can extract it.
$openFirewall = Join-Path $PWD "open-firewall.ps1"
if (Test-Path $openFirewall) {
	jar cfm halma.jar $manifestPath open-firewall.ps1 halma/**
} else {
	jar cfm halma.jar $manifestPath halma/**
}
if ($LASTEXITCODE -ne 0) { Write-Error "Jar creation failed"; Remove-Item $manifestPath -ErrorAction SilentlyContinue; Pop-Location; exit 1 }

Remove-Item $manifestPath -ErrorAction SilentlyContinue
Write-Host "halma.jar created successfully. To run server: java -jar halma.jar -server" -ForegroundColor Green

Pop-Location
