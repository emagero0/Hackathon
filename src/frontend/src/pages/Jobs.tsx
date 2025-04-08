import { JobsHeader } from "../components/jobs/jobs-header"
import { JobsTable } from "../components/jobs/jobs-table"
import { JobsFilter } from "../components/jobs/jobs-filter"

export default function Jobs() {
  return (
    <main className="flex min-h-screen flex-col p-6">
      <JobsHeader />
      <div className="mt-6">
        <JobsFilter />
      </div>
      <div className="mt-6">
        <JobsTable />
      </div>
    </main>
  )
}

