<script setup lang="ts">
import type { AdminPageProjection } from '../domain/admin'
import A100Workspace from './workspaces/A100Workspace.vue'
import A110Workspace from './workspaces/A110Workspace.vue'
import A120Workspace from './workspaces/A120Workspace.vue'
import A121Workspace from './workspaces/A121Workspace.vue'
import A122Workspace from './workspaces/A122Workspace.vue'
import A130Workspace from './workspaces/A130Workspace.vue'
import A140Workspace from './workspaces/A140Workspace.vue'

defineProps<{ projection: AdminPageProjection }>()
defineEmits<{ navigate: [pageId: 'A100' | 'A140']; selectOrder: [orderRef: string]; changed: [] }>()
</script>

<template>
  <A100Workspace v-if="projection.pageId === 'A100'" :projection="projection" @changed="$emit('changed')" />
  <A110Workspace v-else-if="projection.pageId === 'A110'" :projection="projection" @navigate="$emit('navigate', $event)" @changed="$emit('changed')" />
  <A120Workspace v-else-if="projection.pageId === 'A120'" :projection="projection" />
  <A121Workspace v-else-if="projection.pageId === 'A121'" :projection="projection" @changed="$emit('changed')" />
  <A122Workspace v-else-if="projection.pageId === 'A122'" :projection="projection" @changed="$emit('changed')" />
  <A130Workspace v-else-if="projection.pageId === 'A130'" :projection="projection" @changed="$emit('changed')" />
  <A140Workspace v-else :projection="projection" @select-order="$emit('selectOrder', $event)" />
</template>
