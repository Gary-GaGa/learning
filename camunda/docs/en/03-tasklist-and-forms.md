# 03 - Tasklist and forms/task assignment

## Goal

Understand user task assignment (candidate groups/users) and Tasklist operations.

## 1) What are candidate groups?

On a BPMN user task you can configure:

- candidate groups
- candidate users

In [hello-user-task.bpmn](../../examples/hello-user-task.bpmn), the task has:

- `camunda:candidateGroups="demo"`

Meaning: users in group `demo` can see and claim the task.

## 2) Tasklist basics

- **Claim**: assign the task to yourself
- **Complete**: finish the task and move the process forward
- **Unclaim** (if supported): return it to candidate state

## 3) Forms in Camunda 7 (where they live)

Common approaches:

- build forms in your own application (most common)
- use `formKey` to link to your UI
- use embedded forms (older approach)

This tutorial focuses on getting “process + tasks” working end-to-end first.

## Checklist

- You know how candidate groups affect visibility
- You know Claim vs Complete
- You understand forms are typically owned by your app

## Next

Continue to [04 - External Task pattern (and workers)](04-external-task-pattern.md).
