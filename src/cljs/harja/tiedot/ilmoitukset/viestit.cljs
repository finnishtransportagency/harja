(ns harja.tiedot.ilmoitukset.viestit
  "Ilmoituksissa käytetyt UI tapahtumaviestit")

;; Vaihtaa valinnat
(defrecord AsetaValinnat [valinnat])
(defrecord PalautaOletusHakuEhdot [])

(defrecord MuutaIlmoitusHaunSorttausta [kentta])

;; Kun valintojen reaktio muuttuu
(defrecord YhdistaValinnat [valinnat])

(defrecord HaeIlmoitukset []) ;; laukaise ilmoitushaku
(defrecord IlmoitusHaku [tulokset]) ;; Ilmoitusten palvelinhaun tulokset

;; Valitsee ilmoituksen tarkasteltavaksi
(defrecord ValitseIlmoitus [id])

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

;; Aiheiden ja tarkenteiden haku
(defrecord HaeAiheetJaTarkenteet [])
(defrecord HaeAiheetJaTarkenteetOnnistui [vastaus])
(defrecord HaeAiheetJaTarkenteetEpaonnistui [vastaus])

;;
(defrecord AloitaPikakuittaus [ilmoitus kuittaustyyppi])
(defrecord PaivitaPikakuittaus [pikakuittaus])
(defrecord TallennaPikakuittaus [])
(defrecord PeruutaPikakuittaus [])
(defrecord TallennaToimenpiteidenAloitus [id])
(defrecord PeruutaToimenpiteidenAloitus [id])
(defrecord ToimenpiteidenAloitusTallennettu [])
(defrecord ToimenpiteidenAloituksenPeruutusTallennettu [])
(defrecord TallennaToimenpiteidenAloitusMonelle[])
(defrecord ToimenpiteidenAloitusMonelleTallennettu[])
