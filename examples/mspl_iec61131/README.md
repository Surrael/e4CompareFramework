# IEC 61131 MSPL clone-detection fixtures

These are EMSPLE `Configuration` XMI instances using the `.project` extension expected by its IEC reader. They are test fixtures for the framework's IEC tree/MPLE path, not native CODESYS IDE project containers or raw PLCopen XML exports.

Each variant has two similarly structured common POUs (22 ST assignments apiece) plus one variant-specific POU. In the **Create MPLE** preferences, choose `POU` granularity, leave clone size at 20 (each common POU expands to more than 20 comparison nodes), and use the default optional threshold (0.4). Select all three `.project` files together.

Expected signal: selecting each variant in the generated `.mpl` platform should show clone configurations for the repeated common POU class. The exact output can still depend on the installed comparison metric and clustering implementation.
