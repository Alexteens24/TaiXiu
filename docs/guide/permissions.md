# Permissions

<ReferenceTable :columns="['Permission', 'Description', 'Default']" grid="2fr 3fr .7fr">
  <PermissionRow permission="taixiu.use" default-value="true">
    Use the TaiXiu menu and all player subcommands.
  </PermissionRow>
  <PermissionRow permission="taixiu.admin" default-value="op">
    Manage sessions, configuration, health locks, and transaction reconciliation.
  </PermissionRow>
  <PermissionRow permission="taixiu.tax.bypass" default-value="op">
    Receive winnings without the configured payout tax.
  </PermissionRow>
</ReferenceTable>

::: tip Least privilege
Grant `taixiu.admin` only to trusted operators. Its reconciliation commands can intentionally alter balance-related state.
:::
