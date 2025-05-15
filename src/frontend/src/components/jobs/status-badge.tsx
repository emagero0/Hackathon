import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"

type Status = 'pending' | 'in_progress' | 'completed' | 'failed';

interface StatusBadgeProps {
  status: Status;
}

const statusConfig: Record<Status, { className: string; label: string }> = {
  pending: {
    className: "bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200",
    label: "Pending"
  },
  in_progress: {
    className: "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200",
    label: "In Progress"
  },
  completed: {
    className: "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200",
    label: "Completed"
  },
  failed: {
    className: "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200",
    label: "Failed"
  }
};

export function StatusBadge({ status }: StatusBadgeProps) {
  const config = statusConfig[status];
  
  return (
    <Badge variant="outline" className={cn(config.className)}>
      {config.label}
    </Badge>
  );
}
