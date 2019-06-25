(ns harja.tiedot.urakka.toteumat.varusteet.viestit
  "Varustetoteumat osiossa käytetyt UI viestit")

;; Kun tullaan varustenäkymään tai poistutaan siitä
(defrecord Aloita [])
(defrecord Lopeta [])

;; Yhdistä navigaatioatomeista tulevat tiedot
(defrecord YhdistaValinnat [valinnat])

;; Hae varustetoteumat pyynnön laukaisu sekä sen paluuarvo
(defrecord HaeVarusteToteumat [])
(defrecord VarusteToteumatHaettu [toteumat])

;; Valitse annettu toteuma
(defrecord ValitseToteuma [toteuma])
(defrecord TyhjennaValittuToteuma [])

(defrecord ValitseVarusteToteumanTyyppi [tyyppi])

;; Ala luomaan uutta varustetoteumaa käyttöliittymästä
(defrecord UusiVarusteToteuma [toiminto varuste])

;; Aseta uudet varustetoteuman tiedot lomakkeelta muokattaessa
(defrecord AsetaToteumanTiedot [tiedot])

;; Tietolajin vaihtaminen aiheuttaa sen skeeman hakemisen palvelimelta
(defrecord TietolajinKuvaus [tietolaji kuvaus])

;; Uuden varustetoteuman tallennus päättynyt
(defrecord VarustetoteumaTallennettu [hakutulos])

;; Tieosan ajoradat haettu
(defrecord TieosanAjoradatHaettu [ajoradat])

(defrecord LiitteetHaettu [liitteet])

(defrecord VirheTapahtui [virhe])

(defrecord VirheKasitelty [])

;; Varustetoteumat ovat muuttuneet alinäkymässä
(defrecord VarustetoteumatMuuttuneet [varustetoteumat])

;; Ala muokkaamaan valittua varustetta
(defrecord MuokkaaVarustetta [varuste])

(defrecord LisaaLiitetiedosto [liite])
(defrecord PoistaUusiLiitetiedosto [liite])
(defrecord PaivitaLiitteet [liitteet])

(defrecord HaeSijainninOsoite [sijainti])

(defrecord SijanninOsoiteHaettu [osoite])
