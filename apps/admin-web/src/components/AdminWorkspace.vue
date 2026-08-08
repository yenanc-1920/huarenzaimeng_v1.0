<script setup lang="ts">
import type { AdminPageProjection } from '../domain/admin'
import A100Workspace from './workspaces/A100Workspace.vue'
import A110Workspace from './workspaces/A110Workspace.vue'
import A120Workspace from './workspaces/A120Workspace.vue'
import A130Workspace from './workspaces/A130Workspace.vue'
import A140Workspace from './workspaces/A140Workspace.vue'

defineProps<{ projection: AdminPageProjection }>()
defineEmits<{ navigate: [pageId: 'A100' | 'A140'] }>()
</script>

<template>
  <A100Workspace v-if="projection.pageId === 'A100'" :projection="projection" />
  <A110Workspace v-else-if="projection.pageId === 'A110'" :projection="projection" @navigate="$emit('navigate', $event)" />
  <A120Workspace v-else-if="projection.pageId === 'A120'" :projection="projection" />
  <A130Workspace v-else-if="projection.pageId === 'A130'" :projection="projection" />
  <A140Workspace v-else :projection="projection" />
</template>
