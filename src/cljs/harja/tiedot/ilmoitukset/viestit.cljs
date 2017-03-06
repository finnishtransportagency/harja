(ns harja.tiedot.ilmoitukset.viestit
  "Ilmoituksissa käytetyt UI tapahtumaviestit")

;; Vaihtaa valinnat
(defrecord AsetaValinnat [valinnat])

;; Kun valintojen reaktio muuttuu
(defrecord YhdistaValinnat [valinnat])

(defrecord HaeIlmoitukset []) ;; laukaise ilmoitushaku
(defrecord IlmoitusHaku [tulokset]) ;; Ilmoitusten palvelinhaun tulokset


;; Valitsee ilmoituksen tarkasteltavaksi
(defrecord ValitseIlmoitus [ilmoitus])

;; Palvelimelta palautuneet ilmoituksen tiedot
(defrecord IlmoituksenTiedot [ilmoitus])

(defrecord PoistaIlmoitusValinta [])

;; Kuittaukset
(defrecord AvaaUusiKuittaus [])
(defrecord SuljeUusiKuittaus [])

(defrecord AloitaMonenKuittaus [])
(defrecord PeruMonenKuittaus [])
(defrecord ValitseKuitattavaIlmoitus [ilmoitus])

;; asettaa tyypin ja vapaatekstin
(defrecord AsetaKuittausTiedot [tiedot])

;; Tekee kuittauksen palvelimella
(defrecord Kuittaa [])

;; Kuittauksen vastaus
(defrecord KuittaaVastaus [vastaus])
