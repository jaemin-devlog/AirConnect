[CmdletBinding()]
param(
    [string]$CommitRef = 'HEAD',
    [switch]$WorkingTreeOnly
)

# Read-only source/commit gate. Does not read application settings, contact a server,
# stage files, apply SQL, build, push, or deploy. Run separately from behavior tests.
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
function Invoke-ReleaseGit([string[]]$Arguments) {
    $result = @(& git -C $repoRoot -c core.quotepath=false -c core.safecrlf=false @Arguments)
    if ($LASTEXITCODE -ne 0) { throw "Git check failed: $($Arguments[0])" }
    return $result
}

try {
    if ($CommitRef -notmatch '^[A-Za-z0-9][A-Za-z0-9._/-]*$') {
        throw 'Use HEAD, a branch name, tag, or full commit SHA.'
    }
    $resolved = @(Invoke-ReleaseGit @('rev-parse', '--verify', "$CommitRef^{commit}"))[0]
    $required = @(
        'src/main/java/univ/airconnect/admin/AdminController.java',
        'src/main/java/univ/airconnect/admin/AdminService.java',
        'src/main/java/univ/airconnect/admin/AdminGroupMatchingController.java',
        'src/main/java/univ/airconnect/admin/AdminGroupMatchingService.java',
        'src/main/java/univ/airconnect/admin/AdminGroupQueueObserver.java',
        'src/main/java/univ/airconnect/admin/AdminReportController.java',
        'src/main/java/univ/airconnect/admin/AdminReportService.java',
        'src/main/java/univ/airconnect/admin/AdminTicketAdjustmentController.java',
        'src/main/java/univ/airconnect/admin/AdminTicketAdjustmentService.java',
        'src/main/java/univ/airconnect/admin/AdminChatInspectionRetentionWorker.java',
        'src/main/java/univ/airconnect/maintenance/dto/request/MaintenanceContentUpdateRequest.java',
        'src/main/java/univ/airconnect/maintenance/dto/request/MaintenanceStateUpdateRequest.java',
        'src/main/java/univ/airconnect/user/repository/UserTicketLockRepositoryImpl.java',
        'src/main/resources/sql/maintenance_version_migration_mysql.sql',
        'src/main/resources/sql/admin_ticket_adjustments_migration_mysql.sql',
        'src/main/resources/sql/admin_report_handling_migration_mysql.sql',
        'src/main/resources/sql/ticket_history_reference_types_mysql.sql',
        'src/test/java/univ/airconnect/admin/AdminReleaseContractTest.java',
        'src/test/java/univ/airconnect/admin/AdminReportMySqlIntegrationTest.java',
        'src/test/java/univ/airconnect/admin/IsolatedReportMySqlServer.java',
        'src/test/resources/sql/admin_release_legacy_fixture_mysql.sql',
        'src/test/resources/sql/report_legacy_fixture_mysql.sql',
        'src/test/java/univ/airconnect/admin/AdminChatInspectionSecurityTest.java',
        'src/test/java/univ/airconnect/admin/AdminGroupMatchingSecurityTest.java',
        'src/test/java/univ/airconnect/admin/AdminReportHandlingSecurityTest.java',
        'src/test/java/univ/airconnect/admin/AdminTicketAdjustmentIdempotencyTest.java',
        'src/test/java/univ/airconnect/chat/service/GroupChatJoinSecurityTest.java',
        'src/test/java/univ/airconnect/chat/service/ChatSendStatusSecurityTest.java',
        'src/test/java/univ/airconnect/groupmatching/service/GMatchingProcessLockTest.java',
        'tools/isolated-admin-tests.init.gradle',
        'tools/report-mysql-verification.init.gradle',
        'tools/admin-release-check.ps1'
    )
    $missingOnDisk = @($required | Where-Object { -not (Test-Path -LiteralPath (Join-Path $repoRoot $_) -PathType Leaf) })
    if ($missingOnDisk.Count) {
        $missingOnDisk | ForEach-Object { Write-Output "MISSING_WORKTREE $_" }
        exit 1
    }
    Write-Output "Required working-tree files present: $($required.Count)"
    if ($WorkingTreeOnly) {
        Write-Output 'SOURCE_ONLY: existence check passed; NOT deployment approval or behavioral verification.'
        exit 0
    }

    $committedPaths = @(Invoke-ReleaseGit @('ls-tree', '-r', '--name-only', $resolved))
    $missingInCommit = @($required | Where-Object { $_ -notin $committedPaths })
    $scopes = @('build.gradle', 'settings.gradle', 'Dockerfile', '.github/workflows',
        'src/main/java', 'src/main/resources/sql', 'src/test',
        'tools/isolated-admin-tests.init.gradle', 'tools/report-mysql-verification.init.gradle',
        'tools/report-verification-vite.mjs', 'tools/admin-release-check.ps1',
        'tools/admin-release-readiness.md')
    $different = @(Invoke-ReleaseGit (@('diff', '--name-only', $resolved, '--') + $scopes))
    $untracked = @(Invoke-ReleaseGit (@('ls-files', '--others', '--exclude-standard', '--') + $scopes))
    Write-Output "Candidate commit: $resolved"
    $missingInCommit | ForEach-Object { Write-Output "MISSING_COMMIT $_" }
    $different | ForEach-Object { Write-Output "DIFFERS_FROM_COMMIT $_" }
    $untracked | ForEach-Object { Write-Output "UNTRACKED_RELEASE_FILE $_" }
    if ($missingInCommit.Count -or $different.Count -or $untracked.Count) {
        Write-Output 'BLOCKED: commit does not match the reviewed source. Do not deploy this candidate.'
        exit 1
    }
    Write-Output 'COMMIT_MATCH: scoped source matches the candidate. Tests, schema readiness and runtime verification are still required.'
    exit 0
} catch {
    Write-Output "CHECK_FAILED: $($_.Exception.Message)"
    exit 1
}
