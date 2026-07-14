import { defineConfig } from 'vitepress'
import { withMermaid } from 'vitepress-plugin-mermaid'

export default withMermaid(
  defineConfig({
    title: 'TaiXiu',
    description: 'Safe Tai Xiu sessions for Paper and Folia',
    base: '/TaiXiu/',
    cleanUrls: true,
    head: [
      ['link', { rel: 'icon', type: 'image/svg+xml', href: '/TaiXiu/logo.svg' }],
    ],
    themeConfig: {
      logo: '/logo.svg',
      socialLinks: [
        { icon: 'github', link: 'https://github.com/Alexteens24/TaiXiu' },
      ],
      search: { provider: 'local' },
      nav: [
        { text: 'Home', link: '/' },
        { text: 'Download', link: '/guide/download' },
        { text: 'Documentation', link: '/guide/' },
      ],
      sidebar: [
        {
          text: 'Overview',
          items: [
            { text: 'Welcome', link: '/guide/' },
            { text: 'Features', link: '/guide/features' },
            { text: 'Game rules', link: '/guide/gameplay' },
          ],
        },
        {
          text: 'Getting started',
          items: [
            { text: 'Download', link: '/guide/download' },
            { text: 'Installation', link: '/guide/installation' },
            { text: 'Configuration', link: '/guide/configuration' },
          ],
        },
        {
          text: 'Reference',
          items: [
            { text: 'Commands', link: '/guide/commands' },
            { text: 'Permissions', link: '/guide/permissions' },
            { text: 'Placeholders', link: '/guide/placeholders' },
          ],
        },
        {
          text: 'Operations',
          items: [
            { text: 'Storage & recovery', link: '/guide/storage-recovery' },
            { text: 'Troubleshooting', link: '/guide/troubleshooting' },
            { text: 'Runtime validation', link: '/runtime-test-checklist' },
          ],
        },
        {
          text: 'Development',
          items: [
            { text: 'API 3.0', link: '/guide/api' },
            { text: 'API migration', link: '/api-v3-migration' },
            { text: 'Build from source', link: '/guide/development' },
          ],
        },
      ],
      editLink: {
        pattern: 'https://github.com/Alexteens24/TaiXiu/edit/main/docs/:path',
        text: 'Edit this page on GitHub',
      },
      footer: {
        message: 'Released under the GNU GPL v3.0 License.',
        copyright: 'Maintained by Alexteens24 · Original project by CortezRomeo',
      },
    },
  }),
)
