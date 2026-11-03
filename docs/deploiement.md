# Mettre NexaDesk en ligne gratuitement (Oracle Cloud)

NexaDesk tourne sur une machine virtuelle **Always Free** d'Oracle Cloud : gratuite sans limite de durée,
jamais mise en veille, avec la base de données et les pièces jointes conservées. Caddy fournit le HTTPS
(certificat Let's Encrypt, renouvelé automatiquement) et l'adresse est gratuite (`<ip>.sslip.io`).

```
Internet ──HTTPS──▶ Caddy :443 ──▶ Nginx (Angular, /api, /ws) ──▶ API Spring Boot ──▶ PostgreSQL
```

Durée : environ 30 minutes, dont une dizaine de construction sur le serveur.

> Les quotas gratuits d'Oracle évoluent (ils ont été réduits en 2026, à 2 cœurs ARM et 12 Go de mémoire
> au total). Vérifiez la page « Always Free » d'Oracle au moment de créer la VM et restez dans ces limites :
> au-delà, Oracle facture ou arrête les instances.

## 1. Créer le compte Oracle Cloud

1. Inscription sur <https://www.oracle.com/cloud/free/>.
2. Une carte bancaire est demandée pour vérifier l'identité : rien n'est débité tant que le compte reste en
   « Always Free » (ne cliquez pas sur « Upgrade to Pay As You Go »).
3. **Région d'origine** : choisissez-la proche de vos utilisateurs (Paris, Marseille, Madrid…). Elle ne peut
   plus être changée, et les VM gratuites ne sont créées que dans cette région.

## 2. Créer la machine virtuelle

Console Oracle → **Compute → Instances → Create instance** :

| Réglage | Valeur |
|---|---|
| Image | **Canonical Ubuntu 24.04** |
| Shape | **VM.Standard.A1.Flex** (Ampere, ARM) : **2 OCPU, 12 Go** |
| Réseau | VCN par défaut, sous-réseau **public**, « Assign a public IPv4 address » coché |
| Clé SSH | « Generate a key pair for me » → **téléchargez la clé privée** (`ssh-key-….key`) |
| Stockage | 50 Go (inclus dans la gratuité) |

Notez l'**adresse IP publique** affichée une fois la VM démarrée.

« Out of capacity » à la création ? Les machines ARM gratuites sont très demandées : réessayez un peu plus
tard ou choisissez un autre « Availability domain ».

## 3. Ouvrir les ports 80 et 443

Console → la VM → **Subnet** → **Security List** du sous-réseau → **Add Ingress Rules** :

| Source CIDR | Protocole | Port de destination |
|---|---|---|
| `0.0.0.0/0` | TCP | `80` |
| `0.0.0.0/0` | TCP | `443` |

(Le port 80 sert à Let's Encrypt et redirige vers HTTPS. Le pare-feu interne d'Ubuntu est ouvert par le
script d'installation.)

## 4. Envoyer le code sur le serveur

Le code part directement de votre PC vers la VM, par SSH (aucun dépôt public nécessaire). Dans PowerShell,
depuis le dossier `NexaDesk` (remplacez l'IP et le chemin de la clé) :

```powershell
git archive --format=tar.gz -o nexadesk.tar.gz HEAD
scp -i C:\chemin\ssh-key.key nexadesk.tar.gz ubuntu@203.0.113.10:~
ssh -i C:\chemin\ssh-key.key ubuntu@203.0.113.10
```

`git archive` ne prend que les fichiers committés : votre `.env` local et ses secrets ne partent pas.

Si SSH refuse la clé (« permissions too open ») : clic droit sur le fichier → Propriétés → Sécurité →
Avancé → désactiver l'héritage et ne laisser que votre compte.

## 5. Installer

Sur le serveur (session SSH ouverte à l'étape précédente) :

```bash
mkdir -p nexadesk && tar -xzf nexadesk.tar.gz -C nexadesk && cd nexadesk
bash deploy/install.sh --email vous@exemple.fr
```

Le script :

1. installe Docker (paquets Ubuntu) et ouvre les ports 80 / 443 du pare-feu interne ;
2. crée `.env` avec des secrets aléatoires (mot de passe PostgreSQL, clé JWT), lisible par vous seul ;
3. construit les images et démarre PostgreSQL, l'API, Nginx et Caddy ;
4. affiche l'adresse (`https://203-0-113-10.sslip.io`) et le **mot de passe temporaire** de l'administrateur.

À la première connexion, NexaDesk impose de changer ce mot de passe. Créez ensuite les techniciens et les
employés depuis **Administration → Utilisateurs**, puis les catégories et les règles SLA dans **Paramètres**.

### Variantes

- **Démonstration publique** (portfolio) : `bash deploy/install.sh --email vous@exemple.fr --demo` charge les
  données de démonstration et affiche les comptes de démo sur la page de connexion. N'importe quel visiteur
  peut alors se connecter en administrateur et tout modifier : ne l'utilisez pas pour de vraies données.
- **Votre propre nom de domaine** : créez un enregistrement DNS `A` vers l'IP de la VM, puis
  `--domain helpdesk.mon-domaine.fr`.
- **Modèle d'IA réel** : dans `.env`, `AI_MODE=llm`, `AI_PROVIDER` et `AI_API_KEY`, puis relancez le script.
  Sans clé, l'assistant fonctionne en mode heuristique, gratuit, et le dit.

## 6. Mettre à jour

Depuis le PC, recréez et envoyez l'archive (étape 4), puis sur le serveur :

```bash
tar -xzf ~/nexadesk.tar.gz -C ~/nexadesk && cd ~/nexadesk && bash deploy/install.sh
```

Le `.env` existant est conservé (mêmes secrets, mêmes données) ; Flyway applique les migrations de la base
au démarrage de l'API.

## 7. Sauvegarder

La base est dans un volume Docker de la VM. Une sauvegarde quotidienne, à 3 h (`crontab -e`) :

```bash
0 3 * * * cd ~/nexadesk && docker compose exec -T postgres pg_dump -U nexadesk nexadesk | gzip > ~/sauvegarde-$(date +\%u).sql.gz
```

Elle garde les 7 derniers jours. Copiez-les de temps en temps hors de la VM
(`scp -i … ubuntu@IP:~/sauvegarde-*.sql.gz .`).

## Commandes utiles

```bash
cd ~/nexadesk
C="docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml"
$C ps                     # état des services
$C logs -f backend        # journaux de l'API
$C logs caddy             # certificat HTTPS
$C restart backend        # redémarrer l'API
$C down                   # tout arrêter (les données restent)
```
