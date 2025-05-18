import {
  BarChart3,
  FileText,
  Home,
  Settings,
  Bell,
  Search,
  LogOut,
  PanelLeft,
  Users,
  Settings2,
  ClipboardCheck, // Added icon
} from 'lucide-react';
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarSeparator,
  SidebarTrigger,
  useSidebar,
} from './ui/sidebar';
import { useLocation, Link } from 'react-router-dom';
import { ModeToggle } from './mode-toggle';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Avatar, AvatarFallback, AvatarImage } from './ui/avatar';

interface AppSidebarProps {
  userRole: string | null;
  onLogout: () => void;
}

export function AppSidebar({ userRole, onLogout }: AppSidebarProps) {
  const location = useLocation();
  const { state, toggleSidebar } = useSidebar();

  // --- DEBUGGING REMOVED ---
  // console.log("AppSidebar received userRole:", userRole);
  // --- END DEBUGGING ---

  const isActive = (path: string) => {
    return location.pathname === path;
  };

  return (
    <>
      {/* Floating trigger button when sidebar is collapsed */}
      {state === "collapsed" && (
        <Button
          variant="outline"
          size="icon"
          className="fixed left-2 top-2 z-50 md:left-4 md:top-4"
          onClick={toggleSidebar}
        >
          <PanelLeft className="h-4 w-4" />
          <span className="sr-only">Open sidebar</span>
        </Button>
      )}

      <Sidebar collapsible="offcanvas">
        <SidebarHeader className="flex flex-col gap-4 px-4 py-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="rounded-md bg-primary p-1">
                <FileText className="h-6 w-6 text-primary-foreground" />
              </div>
              <h1 className="text-xl font-bold">AI Job Solution</h1>
            </div>
            <SidebarTrigger />
          </div>
          <div className="relative">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input type="search" placeholder="Search jobs..." className="w-full bg-background pl-8" />
          </div>
        </SidebarHeader>

        <SidebarSeparator />

        <SidebarContent>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton asChild isActive={isActive("/")}>
                <Link to="/">
                  <Home className="h-4 w-4" />
                  <span>Dashboard</span>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>

            <SidebarMenuItem>
              <SidebarMenuButton asChild isActive={isActive("/jobs")}>
                <Link to="/jobs">
                  <FileText className="h-4 w-4" />
                  <span>Jobs</span>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>

            {/* Job Verification link visible to all users */}
            <SidebarMenuItem>
              <SidebarMenuButton asChild isActive={isActive("/job-verification")}>
                <Link to="/job-verification">
                  <ClipboardCheck className="h-4 w-4" />
                  <span>Job Verification</span>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>

            <SidebarMenuItem>
              <SidebarMenuButton asChild isActive={isActive("/analytics")}>
                <Link to="/analytics">
                  <BarChart3 className="h-4 w-4" />
                  <span>Analytics</span>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>

            <SidebarMenuItem>
              <SidebarMenuButton asChild isActive={isActive("/settings")}>
                <Link to="/settings">
                  <Settings className="h-4 w-4" />
                  <span>Settings</span>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>

            {userRole === 'admin' && (
              <>
                <SidebarMenuItem>
                  <SidebarMenuButton asChild isActive={isActive('/users')}>
                    <Link to="/users">
                      <Users className="h-4 w-4" />
                      <span>Users</span>
                    </Link>
                  </SidebarMenuButton>
                </SidebarMenuItem>

                <SidebarMenuItem>
                  <SidebarMenuButton asChild isActive={isActive('/configs')}>
                    <Link to="/configs">
                      <Settings2 className="h-4 w-4" />
                      <span>Configs</span>
                    </Link>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              </>
            )}
          </SidebarMenu>
        </SidebarContent>

        <SidebarSeparator />

        <SidebarFooter className="p-4">
          <div className="flex items-center justify-between mb-4">
            <Button variant="outline" size="icon">
              <Bell className="h-4 w-4" />
              <span className="sr-only">Notifications</span>
            </Button>
            <ModeToggle />
            <Button variant="outline" size="icon" onClick={onLogout}>
              <LogOut className="h-4 w-4" />
              <span className="sr-only">Log out</span>
            </Button>
          </div>

          <div className="flex items-center gap-3">
            <Avatar>
              <AvatarImage src="/placeholder.svg?height=40&width=40" alt="User" />
              <AvatarFallback>JD</AvatarFallback> {/* TODO: Update with actual user initials */}
            </Avatar>
            <div className="flex flex-col">
              <span className="text-sm font-medium">John Doe</span> {/* TODO: Update with actual user name */}
              <span className="text-xs text-muted-foreground">{userRole?.replace('_', ' ').replace(/\b\w/g, l => l.toUpperCase()) || 'User'}</span> {/* Display formatted role */}
            </div>
          </div>
        </SidebarFooter>
      </Sidebar>
    </>
  );
}
