# 05 — Design system et maquettes

## 1. Direction artistique

**Mots-clés :** calme, précis, dense sans être chargé. La référence, ce sont les outils SaaS B2B modernes (style Linear, Vercel, Stripe Dashboard) : beaucoup de blanc, une seule couleur d'accent, une typographie soignée, des bordures fines plutôt que des ombres lourdes, et la couleur réservée à ce qui porte du sens (statut, priorité, SLA).

**Principe directeur :** *la couleur est une information*. L'indigo sert aux actions ; le vert, l'orange et le rouge ne servent qu'aux états (succès, risque, retard). Résultat : un ticket en retard se voit au premier coup d'œil, parce que rien d'autre à l'écran n'est rouge.

## 2. Design tokens

Implémentés en **CSS custom properties** dans `styles/_tokens.scss`. Le thème sombre redéfinit les mêmes variables sous `[data-theme="dark"]` : les composants n'ont jamais besoin de savoir dans quel thème ils sont.

### 2.1 Couleurs

| Token | Clair | Sombre | Usage |
|---|---|---|---|
| `--color-primary` | `#4F46E5` | `#818CF8` | Boutons principaux, liens, focus, élément actif |
| `--color-primary-hover` | `#4338CA` | `#A5B4FC` | Survol |
| `--color-primary-subtle` | `#EEF2FF` | `#1E1B4B` | Fond de l'élément de navigation actif, sélection |
| `--color-bg` | `#F8FAFC` | `#0B1120` | Fond de page |
| `--color-surface` | `#FFFFFF` | `#111827` | Cartes, tableaux, drawers |
| `--color-surface-raised` | `#FFFFFF` | `#1F2937` | Menus, popovers, modales |
| `--color-sidebar` | `#0F172A` | `#020617` | Sidebar (slate) |
| `--color-border` | `#E2E8F0` | `#1F2937` | Bordures |
| `--color-text` | `#0F172A` | `#F1F5F9` | Texte principal |
| `--color-text-muted` | `#64748B` | `#94A3B8` | Texte secondaire, libellés |
| `--color-success` | `#16A34A` | `#22C55E` | Résolu, SLA respecté |
| `--color-warning` | `#F59E0B` | `#FBBF24` | À risque, en attente |
| `--color-danger` | `#DC2626` | `#F87171` | Critique, SLA dépassé, actions destructrices |
| `--color-info` | `#0284C7` | `#38BDF8` | Informations, nouveau |

**Accessibilité des couleurs :** l'orange `#F59E0B` sur fond blanc ne passe pas le contraste WCAG AA pour du texte (environ 2,1:1). Les badges utilisent donc le schéma **fond clair + texte foncé de la même teinte** (ex. fond `#FEF3C7`, texte `#B45309`), qui dépasse 4,5:1. Le même principe vaut pour tous les badges. Aucune information ne repose **uniquement** sur la couleur : chaque badge a aussi un libellé, et la priorité a une icône.

### 2.2 Badges métier

| Statut | Teinte | | Priorité | Teinte | Icône Lucide |
|---|---|---|---|---|---|
| `NEW` | info (bleu ciel) | | `LOW` | slate | `signal-low` |
| `ASSIGNED` | indigo | | `MEDIUM` | bleu | `signal-medium` |
| `IN_PROGRESS` | violet | | `HIGH` | orange | `signal-high` |
| `PENDING` | ambre | | `CRITICAL` | rouge, avec un point pulsant | `alert-octagon` |
| `RESOLVED` | vert | | | | |
| `CLOSED` | slate | | | | |
| `REJECTED` | rouge atténué | | | | |

**Indicateur SLA :** une barre de progression fine (consommé / total) + le temps restant (« 1 h 24 restantes », « Dépassé de 35 min »). Elle est verte, orange à partir de 75 %, rouge au-delà de l'échéance, et grise avec l'icône `pause` quand le ticket est en `PENDING`.

### 2.3 Typographie

- Police : **Inter** (variable, chargée localement depuis `assets/` pour ne dépendre d'aucun CDN), avec `font-feature-settings: "cv11", "tnum"` : les **chiffres tabulaires** gardent les KPI et les colonnes alignés.
- Monospace : **JetBrains Mono** pour les références (`INC-2026-0042`), les numéros de série et les codes.

| Token | Taille / interligne | Graisse | Usage |
|---|---|---|---|
| `--text-display` | 30 / 36 | 600 | Valeur des KPI |
| `--text-h1` | 24 / 32 | 600 | Titre de page |
| `--text-h2` | 18 / 28 | 600 | Titre de section, de carte |
| `--text-body` | 14 / 20 | 400 | Texte courant, tableaux |
| `--text-small` | 13 / 18 | 400–500 | Libellés, métadonnées |
| `--text-xs` | 12 / 16 | 500 | Badges, en-têtes de colonnes (majuscules + espacement) |

### 2.4 Espacements, rayons, ombres, mouvements

- **Espacements** sur une grille de 4 px : `4, 8, 12, 16, 20, 24, 32, 40, 48, 64`.
- **Rayons** : `--radius-sm 6px` (badges, champs), `--radius-md 10px` (boutons, cartes), `--radius-lg 14px` (modales, drawers), `--radius-full` (avatars).
- **Ombres** : `--shadow-sm` (cartes au survol), `--shadow-md` (menus), `--shadow-lg` (modales). Elles restent très discrètes ; en sombre, elles sont remplacées par des bordures plus claires.
- **Mouvements** : `--duration-fast 120ms` (survol), `--duration-base 200ms` (ouverture de menus), `--duration-slow 300ms` (drawer), easing `cubic-bezier(0.2, 0, 0, 1)`. Avec `prefers-reduced-motion: reduce`, toutes les animations sont désactivées.

### 2.5 Points de rupture

| Nom | Largeur | Comportement |
|---|---|---|
| mobile | < 640 px | Sidebar en tiroir (bouton menu), KPI sur 1 colonne, tableau des tickets affiché en **cartes empilées**, drawer de détail en plein écran |
| tablette | 640–1023 px | Sidebar repliée en icônes (64 px), KPI sur 2 colonnes |
| desktop | ≥ 1024 px | Sidebar dépliée (248 px, repliable avec mémorisation), KPI sur 4 colonnes |

## 3. Bibliothèque de composants

| Composant | Variantes / comportement |
|---|---|
| `Button` | `primary`, `secondary`, `ghost`, `danger` · tailles `sm` / `md` · état `loading` (spinner + désactivé) · icône seule avec `aria-label` obligatoire |
| `StatCard` | Icône dans une pastille colorée, valeur, libellé, **tendance** (↑ 12 % par rapport à la période précédente, calculée côté API), skeleton pendant le chargement |
| `DataTable` | En-têtes triables (`aria-sort`), sélection de ligne, ligne cliquable qui ouvre le drawer, actions rapides au survol, skeleton de 8 lignes, empty state, colonnes masquables sur mobile |
| `FilterBar` | « Chips » de filtres actifs (supprimables), menu d'ajout de filtre, bouton « Réinitialiser », synchronisation avec l'URL |
| `SearchBar` | Raccourci `Ctrl+K` / `⌘K` pour la recherche globale, debounce de 300 ms, résultats groupés, navigation au clavier |
| `Drawer` | Panneau latéral à droite (640 px), `Esc` pour fermer, focus piégé, URL mise à jour (`/tickets?open=INC-2026-0042`) |
| `ConfirmDialog` | Titre, conséquence expliquée, bouton danger ; saisie du motif quand c'est requis (rejet, réouverture) |
| `Timeline` | Icône par type d'événement, acteur, temps relatif (et date exacte en infobulle), diff ancienne → nouvelle valeur |
| `Toast` | `success`, `error`, `info`, `warning` · 4 s · pile en bas à droite · `role="status"` / `aria-live` |
| `EmptyState` | Illustration SVG au trait (couleur primaire, cohérente avec les icônes), titre, explication, **action utile** (« Créer un ticket ») |
| `Skeleton` | Blocs gris animés (shimmer) aux dimensions du contenu final, pour éviter les sauts de mise en page |
| `NotificationCenter` | Popover depuis la cloche, badge du nombre de non lues, onglets « Non lues / Toutes », « Tout marquer comme lu », clic → navigation vers le ticket |
| `AiSuggestionCard` | Bordure dégradée indigo, icône `sparkles`, **badge de source** (« Modèle IA » ou « Mode démo — heuristique »), boutons « Appliquer » / « Ignorer » |

## 4. Maquettes (wireframes)

### 4.1 Connexion

```
┌──────────────────────────────────────┬──────────────────────────────────────────┐
│                                      │                                          │
│   ◆ NexaDesk                         │   ░░ Panneau visuel ░░                   │
│                                      │   Dégradé indigo → slate, aperçu stylisé │
│   Bon retour                         │   du dashboard en arrière-plan flouté     │
│   Connectez-vous à votre espace      │                                          │
│                                      │   « Résolvez plus vite.                  │
│   E-mail                             │     Mesurez ce qui compte. »             │
│   [ nom@entreprise.com          ]    │                                          │
│   Mot de passe                       │   ✓ SLA suivis en temps réel             │
│   [ ••••••••••             👁 ]      │   ✓ Notifications instantanées           │
│                                      │   ✓ Suggestions intelligentes            │
│   [        Se connecter        ]     │                                          │
│                                      │                                          │
│   ── Comptes de démonstration ──     │                                          │
│   [Admin] [Technicien] [Employé]     │                                          │
└──────────────────────────────────────┴──────────────────────────────────────────┘
  (mobile : panneau visuel masqué, formulaire centré)
```

### 4.2 Shell + dashboard admin

```
┌────────────┬─────────────────────────────────────────────────────────────────────┐
│ ◆ NexaDesk │  Accueil / Dashboard        [🔍 Rechercher…  Ctrl K]   ☾  🔔3  (AB)▾ │
│            ├─────────────────────────────────────────────────────────────────────┤
│ ▣ Dashboard│  Bonjour Amina                                                      │
│ ▤ Tickets  │  Vue d'ensemble · 30 derniers jours ▾              [+ Nouveau ticket]│
│ ⚒ Interv.  │                                                                     │
│ ▢ Équipem. │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐                │
│ ◎ Utilisat.│  │▤ Total   │ │◷ Ouverts │ │⚠ En retard│ │✓ Résol.  │                │
│ ▥ Rapports │  │  248     │ │  37      │ │   6      │ │  91 %    │                │
│ ✦ Assistant│  │ ↑12 %    │ │ ↓4 %     │ │ ↑2       │ │ ↑3 pts   │                │
│ ☰ Base conn│  └──────────┘ └──────────┘ └──────────┘ └──────────┘                │
│ ⚙ Paramètr.│  ┌───────────────────────────────────┐ ┌───────────────────────┐    │
│ ⎙ Audit    │  │ Évolution des tickets   [Jour|Mois]│ │ Par priorité (donut)  │    │
│            │  │  ╱╲    ╱╲╱╲   — créés              │ │      ◯ 248            │    │
│            │  │ ╱  ╲╱╲╱    ╲  — résolus            │ │  ● Critique  ● Haute  │    │
│            │  └───────────────────────────────────┘ └───────────────────────┘    │
│            │  ┌───────────────────────────────────┐ ┌───────────────────────┐    │
│            │  │ Respect SLA   Réponse 94 % ▓▓▓▓▓░  │ │ Activité récente      │    │
│            │  │               Résolution 87 % ▓▓▓▓░│ │ • Karim a résolu INC… │    │
│            │  ├───────────────────────────────────┤ │ • INC-0042 dépasse SLA│    │
│            │  │ Performance techniciens (barres)   │ │ • Sara a créé REQ-…   │    │
│            │  └───────────────────────────────────┘ └───────────────────────┘    │
│ «  Replier │  ┌ Derniers tickets ─────────────────────────────── Voir tout → ┐   │
│ (AB) Amina │  │ INC-2026-0048  VPN inaccessible   ●Critique  En cours  ▓▓░ 42m│  │
└────────────┴──┴──────────────────────────────────────────────────────────────┴───┘
```
*(Les symboles des wireframes ne sont que des repères : toutes les icônes réelles seront des icônes Lucide, sans emoji.)*

### 4.3 Liste des tickets — vue tableau

```
  Tickets                                             [▤ Tableau | ▥ Kanban]  [+ Nouveau]
  [🔍 Rechercher par référence, titre…]  [Statut ▾] [Priorité ▾] [Catégorie ▾] [SLA ▾] [+ Filtre]
  Filtres actifs : (Statut : En cours ✕) (Priorité : Haute, Critique ✕)   Réinitialiser

  ┌──┬───────────────┬──────────────────────────┬──────────┬───────────┬──────────┬──────────┬────┐
  │☐ │ Référence ↓   │ Titre                    │ Priorité │ Statut    │ Assigné  │ SLA      │    │
  ├──┼───────────────┼──────────────────────────┼──────────┼───────────┼──────────┼──────────┼────┤
  │☐ │ INC-2026-0048 │ VPN inaccessible…        │ ●Critique│ En cours  │ (KB) K.B │ ▓▓▓░ 42m │ ⋯  │
  │☐ │ REQ-2026-0019 │ Accès dossier Finance    │ ●Moyenne │ Assigné   │ (SL) S.L │ ▓░░░ 6h  │ ⋯  │
  │☐ │ INC-2026-0045 │ Imprimante 2e étage      │ ●Haute   │ En attente│ (KB) K.B │ ⏸ Pause  │ ⋯  │
  └──┴───────────────┴──────────────────────────┴──────────┴───────────┴──────────┴──────────┴────┘
  248 tickets · 20 par page ▾                                        ‹ 1 2 3 … 13 ›
```

### 4.4 Vue Kanban

```
  NOUVEAU (8)        ASSIGNÉ (5)        EN COURS (11)      EN ATTENTE (4)     RÉSOLU (9)
  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
  │INC-2026-0050 │   │REQ-2026-0019 │   │INC-2026-0048 │   │INC-2026-0045 │   │INC-2026-0040 │
  │Écran noir    │   │Accès Finance │   │VPN inacc.    │   │Imprimante    │   │Outlook lent  │
  │●Haute   ▓░ 3h│   │●Moy.    ▓░ 6h│   │●Crit.  ▓▓▓42m│   │●Haute  ⏸     │   │✓ SLA tenu    │
  │(SA)          │   │(SL)          │   │(KB)          │   │(KB)          │   │(MR)          │
  └──────────────┘   └──────────────┘   └──────────────┘   └──────────────┘   └──────────────┘
```
Glisser-déposer avec Angular CDK. Une colonne n'accepte une carte que si la transition est autorisée : pendant le glisser, les colonnes non valides sont grisées. Si la transition demande un motif (PENDING, REJECTED), une modale s'ouvre au dépôt. En cas de refus du serveur, la carte revient à sa place avec un toast d'erreur (mise à jour **optimiste** annulée).

### 4.5 Détail d'un ticket (drawer ou page)

```
  ┌─ INC-2026-0048 ─────────────────────────────────────────── [⤢ Plein écran] [✕] ┐
  │ VPN inaccessible depuis le télétravail                                          │
  │ ●Critique   En cours   Réseau › VPN       [Changer le statut ▾]  [⋯]            │
  ├──────────────────────────────────────────────────┬──────────────────────────────┤
  │ [Conversation] [Interventions] [Historique] [IA] │ Demandeur   (SA) Sara A.     │
  │                                                  │ Assigné     (KB) Karim B. ✎  │
  │ Description                                      │ Équipement  EQ-0007 Laptop   │
  │ Depuis ce matin, connexion VPN refusée…          │ Créé        il y a 1 h 18    │
  │ 📎 capture-erreur.png  (123 Ko)                  │──────────────────────────────│
  │                                                  │ SLA                          │
  │ ── Sara A. · il y a 1 h ──────────────────       │ Réponse    ✓ tenue (9 min)   │
  │ Le message d'erreur est « 809 »                  │ Résolution ▓▓▓▓▓▓░ 42 min    │
  │ ── Karim B. · interne 🔒 ────────────── (fond    │            Échéance 11:30    │
  │ Probable certificat expiré côté passerelle  ambre)│──────────────────────────────│
  │                                                  │ ✦ Tickets similaires (3)     │
  │ ┌──────────────────────────────────────────────┐ │ INC-2026-0012 · 87 %         │
  │ │ Répondre…                    [Public|Interne]│ │ « Certificat VPN expiré »    │
  │ │ 📎                                 [Envoyer] │ │ Mode démo — heuristique      │
  │ └──────────────────────────────────────────────┘ │                              │
  └──────────────────────────────────────────────────┴──────────────────────────────┘
```

### 4.6 Création d'un ticket (formulaire en 3 étapes)

```
  ① Type ──── ② Détails ──── ③ Récapitulatif

  ② Détails
  Titre *            [ Mon écran reste noir au démarrage        ]   35/150
  Description *      [ …                                         ]
                     ┌ ✦ Suggestion · Mode démo — heuristique ───────────────────┐
                     │ Catégorie : Matériel › Écran   Priorité : Moyenne          │
                     │                                   [Appliquer] [Ignorer]    │
                     └────────────────────────────────────────────────────────────┘
                     ┌ Ce problème a peut-être déjà une solution ────────────────┐
                     │ 📄 « Écran noir : vérifier le câble et la source d'entrée »│
                     └────────────────────────────────────────────────────────────┘
  Catégorie *        [ Matériel ▾ ]     Sous-catégorie [ Écran ▾ ]
  Impact *           ( ) Moi seul   (•) Mon équipe   ( ) Toute l'entreprise
  Urgence *          ( ) Peut attendre  (•) Gênant  ( ) Bloquant
                     → Priorité calculée : ● Moyenne · Résolution sous 24 h ouvrées
  Équipement         [ EQ-0003 · Dell P2422H ▾ ]   (uniquement mes équipements)
  Pièces jointes     [ Glissez vos fichiers ici ou parcourez ]  PNG, JPG, PDF · 10 Mo max

                                               [Retour]  [Continuer →]
```

### 4.7 Dashboards technicien et employé

- **Technicien :** KPI (Mes tickets, En attente, Urgents, Résolus cette semaine, Temps moyen), une file « **À traiter maintenant** » triée par échéance SLA, un donut de répartition par catégorie, et un bloc « Tickets non assignés de mes spécialités » avec un bouton « Prendre en charge ».
- **Employé :** en tête, une grande carte « Un problème ? Décrivez-le » qui ouvre le formulaire, puis 3 KPI (Ouverts, En cours, Résolus), « Mes dernières demandes » sous forme de cartes avec une barre de progression du workflow, les tickets « À confirmer » mis en avant (résolus en attente de sa confirmation), et l'accès à l'assistant IT.

## 5. UX transverse

| Sujet | Règle |
|---|---|
| Feedback | Chaque action produit un retour visible : toast, mise à jour immédiate de la ligne, compteur animé |
| Chargement | Skeletons pour le premier affichage ; spinner uniquement dans le bouton pour les actions ; aucune page blanche |
| Empty states | Toujours une explication et une action (« Aucun ticket ne correspond à ces filtres » → [Réinitialiser les filtres]) |
| Confirmation | Obligatoire pour : rejeter, désactiver un utilisateur, réaffecter, mettre un équipement au rebut, supprimer une pièce jointe |
| Clavier | Ordre de tabulation logique, `:focus-visible` avec un anneau indigo de 2 px, `Esc` ferme les overlays, `Ctrl+K` lance la recherche, `N` crée un nouveau ticket (sur la page Tickets), flèches dans les menus |
| Lecteurs d'écran | Landmarks (`nav`, `main`, `header`), `aria-live` pour les toasts et les notifications, libellés sur les boutons-icônes, tableaux sémantiques |
| Formulaires | Libellés visibles (jamais un simple placeholder), astérisque sur les champs obligatoires, erreur sous le champ, en rouge, avec une icône |
| Temps | Temps relatif (« il y a 5 min ») avec la date exacte en infobulle ; affichage dans le fuseau de l'entreprise |
| Langue | Interface en **français** ; les libellés sont centralisés dans un fichier pour permettre une internationalisation future |

## 6. Prochaine étape design

Avant d'attaquer le frontend (Phase 4), je peux produire une **maquette HTML interactive** (dashboard admin et page tickets, en clair et en sombre) à partir de ces tokens, afin de valider le rendu visuel réel plutôt que des wireframes ASCII.
