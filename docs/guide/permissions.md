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
  <PermissionRow permission="taixiu.maxbet.&lt;amount&gt;" default-value="not set">
    Override the configured maximum bet; the highest enabled numeric node wins.
  </PermissionRow>
  <PermissionRow permission="taixiu.tax.discount.&lt;points&gt;" default-value="not set">
    Subtract percentage points from payout tax; decimals are accepted and bypass takes precedence.
  </PermissionRow>
  <PermissionRow permission="taixiu.rollover" default-value="true">
    Use a winning escrow as the next session's complete stake.
  </PermissionRow>
  <PermissionRow permission="taixiu.insurance.claim" default-value="false">
    Qualify wallet-funded bets for configured loss-streak insurance.
  </PermissionRow>
</ReferenceTable>

::: tip Least privilege
Grant `taixiu.admin` only to trusted operators. Its reconciliation commands can intentionally alter balance-related state.
:::
