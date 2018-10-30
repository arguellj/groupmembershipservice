# Group Membership Service

## Summary

Distributed systems consist of groups of processes that cooperate in order to complete specific tasks. A Group Membership Protocol is of particular use in such systems, providing processes in a group with a consistent view of the membership of that group. In this way, when a membership change occurs, processes can agree on which of them must complete a pending task or start a new task. The problem of reaching a consistent membership view is very similar to the one of achieving common knowledge in a distributed system, commonly referred to as the Consensus Problem.The Consensus Problem has been proven insolvable in asynchronous systems with crash failures.

Group Membership differs from Consensus in that the value to be agreed upon, namely, the current membership view, may change due to asynchronous failures. Moreover,
while Consensus requires all nonfaulty processes to reach the same decision, Group Membership usually allows the removal of nonfaulty processes from the group when they are erroneously suspected to have crashed, thus requiring agreement only on a subset of the processes in the system. Despite these differences, Chandra et al. recently adapted the impossibility result for the Consensus Problem to the Group Membership Problem. At the same time, they conjectured that techniques used to circumvent the impossibility of Consensus can be applied to solve the Group Membership Problem. Such techniques include using randomization, probability assumptions on the behavior of the system, and using failure detectors that are defined in terms of global accuracy and completeness system properties.

## Understand our Group Membership System

Read the file 'Group membership service Report - Jeancarlo Arguello Calvo.pdf' for more details.

## Who do I talk to

* Jeancarlo Arguello Calvo - arguellj@tcd.ie
* Trinity College Dublin, The University of Dublin