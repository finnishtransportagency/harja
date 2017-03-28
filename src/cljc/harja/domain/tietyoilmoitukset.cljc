(ns harja.domain.tietyoilmoitukset)

(def kaistajarjestelyt
  {"ajokaistaSuljettu" "Ajokaista suljettu"
   "ajorataSuljettu" "Ajorata suljettu"
   "tieSuljettu" "Tie suljettu"})

(defn kaistajarjestelyt->str [t]
  (str (::kaistajarjestelyt t)))

(defn nopeusrajoitukset->str [t]
  (str (::nopeusrajoitukset t)))

(defn tyoajat->str [t]
  (str (::tyoajat t)))

(defn tyotyypit->str [t]
  (str (::tyotyypit t)))

(defn tienpinnat->str [t]
  (str (::tienpinnat t)))

(defn kiertotienpinnat->str [t]
  (str (::kiertotienpinnat t)))

(defn ajoneuvorajoitukset->str [t]
  (str (::ajoneuvorajoitukset t)))

(defn huomautukset->str [t]
  (str (::huomautukset t)))

(defn ajoittaiset-pysatykset->str [t]
  (str (::ajoittaiset-pysatykset t)))

(defn ajoittain-suljettu-tie->str [t]
  (str (::ajoittain-suljettu-tie t)))