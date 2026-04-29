rootProject.name = "fcfs-reservation"

include(
    "apps:domain",
    "apps:application",
    "apps:api",
    "storage:rdb",
    "storage:redis",
)
