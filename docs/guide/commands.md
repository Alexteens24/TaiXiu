# Commands

Command and alias badges below can be clicked to copy them. Vietnamese aliases use unaccented ASCII so they remain convenient in the Minecraft command box.

## Player commands

<ReferenceTable :columns="['Command', 'Description']">
  <CommandRow commands="/taixiu" :aliases="['/choitaixiu', '/playtaixiu']" permission="taixiu.use">
    Open the main menu.
  </CommandRow>
  <CommandRow commands="/taixiu bet &lt;tai|xiu&gt; &lt;amount&gt;" aliases="/taixiu cuoc &lt;tai|xiu&gt; &lt;amount&gt;" permission="taixiu.use">
    Place a Tai or Xiu bet using the current session currency.
  </CommandRow>
  <CommandRow commands="/taixiu info [session]" aliases="/taixiu thongtin [session]" permission="taixiu.use">
    Open the active session or a historical session by ID.
  </CommandRow>
  <CommandRow commands="/taixiu rules" aliases="/taixiu luatchoi" permission="taixiu.use">
    Show the game rules.
  </CommandRow>
  <CommandRow commands="/taixiu toggle" permission="taixiu.use">
    Toggle TaiXiu notifications and boss bar visibility for the player.
  </CommandRow>
</ReferenceTable>

## Administration

<ReferenceTable :columns="['Command', 'Description']">
  <CommandRow commands="/taixiuadmin reload" permission="taixiu.admin">
    Reload configuration and reloadable integrations.
  </CommandRow>
  <CommandRow commands="/taixiuadmin changestate" permission="taixiu.admin">
    Pause or resume the game when the health lock permits it.
  </CommandRow>
  <CommandRow commands="/taixiuadmin settime &lt;seconds&gt;" permission="taixiu.admin">
    Replace the active session countdown.
  </CommandRow>
  <CommandRow commands="/taixiuadmin setcurrency &lt;VAULT|PLAYERPOINTS&gt;" permission="taixiu.admin">
    Change currency before the current session receives any bet.
  </CommandRow>
  <CommandRow commands="/taixiuadmin setresult &lt;d1&gt; &lt;d2&gt; &lt;d3&gt;" permission="taixiu.admin">
    Force the three dice used for the session result.
  </CommandRow>
  <CommandRow commands="/taixiuadmin health" aliases="/taixiuadmin suckhoe" permission="taixiu.admin">
    Inspect database, economy, and unresolved transaction health.
  </CommandRow>
  <CommandRow commands="/taixiuadmin health acknowledge" aliases="/taixiuadmin suckhoe xacnhan" permission="taixiu.admin">
    Explicitly acknowledge and clear a health lock after its cause has been checked.
  </CommandRow>
  <CommandRow commands="/taixiuadmin transaction list [page] [status]" aliases="/taixiuadmin giaodich danhsach [page] [status]" permission="taixiu.admin">
    List journal entries that require operator attention.
  </CommandRow>
  <CommandRow commands="/taixiuadmin transaction &lt;id&gt; &lt;action&gt; confirm [reason]" aliases="/taixiuadmin giaodich &lt;id&gt; &lt;action&gt; xacnhan [reason]" permission="taixiu.admin">
    Reconcile one transaction and record an audit reason. Actions support `complete`/`hoantat`, `fail`/`thatbai`, `refund`/`hoantien`, and `retry`/`thulai`.
  </CommandRow>
</ReferenceTable>

::: danger Reconciliation changes balances or audit state
Inspect the economy provider ledger and the player's actual balance before using `refund`, `retry`, `complete`, or `fail`. Never blindly retry an `UNKNOWN` operation.
:::
