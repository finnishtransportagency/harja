module.exports = {
  "stories": [
    "../dev-resources/js/out/harja/stories/**/*_stories.mdx",
    "../dev-resources/js/out/harja/stories/**/*_stories.@(js|jsx|ts|tsx)"
  ],
  "addons": [
    "@storybook/addon-links",
    "@storybook/addon-essentials"
  ],
  "staticDirs": [
    "../dev-resources/"
  ],
  "core": {
    "builder": "webpack5"
  },
  "framework": "@storybook/react"
}