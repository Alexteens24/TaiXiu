import DefaultTheme from 'vitepress/theme'
import './style.css'

import CardGrid from '../components/CardGrid.vue'
import DocCard from '../components/DocCard.vue'
import ReferenceTable from '../components/ReferenceTable.vue'
import CommandRow from '../components/CommandRow.vue'
import PermissionRow from '../components/PermissionRow.vue'

export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    app.component('CardGrid', CardGrid)
    app.component('DocCard', DocCard)
    app.component('ReferenceTable', ReferenceTable)
    app.component('CommandRow', CommandRow)
    app.component('PermissionRow', PermissionRow)
  },
}
