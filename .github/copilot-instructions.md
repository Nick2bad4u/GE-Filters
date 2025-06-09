---
# Codacy Rules
Configuration for AI behavior when interacting with Codacy
## When after ANY successful edit_file or reapply tool call
IMMEDIATELY run codacy_cli_analyze tool for each edited file with:
 - 1. rootPath set to the workspace path
 - 2. file set to the edited file path
 - 3. no tool
 - AT THE END run codacy_cli_analyze tool for trivy for the entire project with:
 - 1. rootPath set to the workspace path
 - 2. no file
 - 3. tool set to "trivy"
 - If issues are found, propose fixes for them

## When after adding new dependencies, libraries or packages
IMMEDIATELY run codacy_cli_analyze tool for trivy for:
 - 1. rootPath set to the workspace path
 - 2. no file
 - 3. tool set to "trivy"
 - If issues are found, propose fixes for them

## When a call to a Codacy tool that has repository or organization as parameter returns 404 error
run codacy_setup_repository tool and then retry the action that failed
---