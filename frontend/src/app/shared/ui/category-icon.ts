import {
  AppWindow,
  Cloud,
  Database,
  Folder,
  Headphones,
  KeyRound,
  Laptop,
  Lock,
  LucideIconData,
  Mail,
  Monitor,
  Network,
  Phone,
  Printer,
  Server,
  ShieldAlert,
  Smartphone,
  Tag,
  Wifi,
} from 'lucide-angular';

/**
 * Icons a category may name (Lucide names, chosen by admins in the settings). Only these are bundled: an
 * unknown name shows the generic tag rather than loading the whole icon set.
 */
const ICONS: Record<string, LucideIconData> = {
  'app-window': AppWindow,
  cloud: Cloud,
  database: Database,
  folder: Folder,
  headphones: Headphones,
  'key-round': KeyRound,
  laptop: Laptop,
  lock: Lock,
  mail: Mail,
  monitor: Monitor,
  network: Network,
  phone: Phone,
  printer: Printer,
  server: Server,
  'shield-alert': ShieldAlert,
  smartphone: Smartphone,
  tag: Tag,
  wifi: Wifi,
};

export function categoryIcon(name: string | null | undefined): LucideIconData {
  return (name && ICONS[name]) || Tag;
}
