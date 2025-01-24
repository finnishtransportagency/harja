(ns harja.palvelin.palvelut.hallinta.tarjoushinnat
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.budjettisuunnittelu :as budjettisuunnittelu-q]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))

(defn- muodosta-tarjoushinnat [tarjoushinnat]
  (let [groupatut-tarjoushinnat (group-by (juxt :urakka :urakka-nimi :urakka-paattynyt? :urakan_pituus) tarjoushinnat)
        ;; Loopataan groupatut urakat ja niiden tarjoushinnat läpi
        kasitellyt-tarjoushinnat (map (fn [[[urakka urakka-nimi urakka-paattynyt? urakan_pituus] urakan-tarjoushinnat]]
                                        ;; Varmistetaan, että jos urakalla ei ole jollekin vuodedelle urakka_tarjous taulussa vielä riviä, niin
                                        ;; siitä silti muodostetaan mäppi, jotta sille voidaan syöttää summa käyttöliittymässä
                                        (let [muodostetut-tarjoushinnat-urakalle (reduce (fn [acc vuosinro]
                                                                                           (conj acc {:urakka urakka
                                                                                                      :urakka-nimi urakka-nimi
                                                                                                      ;; Asetetaan id (urakka_tavoite -taulu), jos se saatiin tietokannasta
                                                                                                      :id (when (>= (count urakan-tarjoushinnat) vuosinro)
                                                                                                            (:id (nth urakan-tarjoushinnat (dec vuosinro))))
                                                                                                      :urakka-paattynyt? urakka-paattynyt?
                                                                                                      :hoitokausi vuosinro
                                                                                                      ;; Asetetaan tavoitehinta, jos se saatiin kannasta
                                                                                                      :tarjous-tavoitehinta (when (>= (count urakan-tarjoushinnat) vuosinro)
                                                                                                                              (:tarjous-tavoitehinta (nth urakan-tarjoushinnat (dec vuosinro))))

                                                                                                      ;; Asetetaan päätös, jos se saatiin kannasta
                                                                                                      :on-paatos (when (>= (count urakan-tarjoushinnat) vuosinro)
                                                                                                                   (:on-paatos (nth urakan-tarjoushinnat (dec vuosinro))))}))
                                                                                   []
                                                                                   (range 1 urakan_pituus))
                                              ;; Kokoa urakan tiedot yhteen mäppiin ja lisää jokaiselle hoitovuodelle oma tarjoushinta :tarjoushinnat avaimeen
                                              urakka-rivi {:urakka urakka
                                                           :urakka-nimi urakka-nimi
                                                           :urakka-paattynyt? urakka-paattynyt?
                                                           :puutteellisia? (some nil? (map :tarjous-tavoitehinta muodostetut-tarjoushinnat-urakalle))
                                                           :tarjoushinnat muodostetut-tarjoushinnat-urakalle}]
                                          urakka-rivi))
                                   groupatut-tarjoushinnat)]
    ;; Palauta tarjoushinnat ui:lle
    kasitellyt-tarjoushinnat))

(defn- hae-tarjoushinnat [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-tarjoushinnat kayttaja)
  (muodosta-tarjoushinnat (budjettisuunnittelu-q/hae-urakoiden-tarjoushinnat db)))

(defn- paivita-tarjoushinnat [db kayttaja tiedot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-tarjoushinnat kayttaja)
  (doseq [{:keys [id tarjous-tavoitehinta hoitokausi urakka]} tiedot]
    ;; Jos tarjoushinta on kerran syötetty, niin rivillä on id ja me voidaan vain päivittää arvoja.
    (if id
      (budjettisuunnittelu-q/paivita-tarjoushinta<! db {:id id
                                                        :kayttaja_id (:id kayttaja)
                                                        :tarjous-tavoitehinta tarjous-tavoitehinta})
      (budjettisuunnittelu-q/lisaa-tarjoushinta<! db {:kayttaja_id (:id kayttaja)
                                                      :tarjous-tavoitehinta tarjous-tavoitehinta
                                                      :urakka_id urakka
                                                      :hoitokausi hoitokausi})))
  (muodosta-tarjoushinnat (budjettisuunnittelu-q/hae-urakoiden-tarjoushinnat db)))

(defrecord TarjoushinnatHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :hae-tarjoushinnat
      (fn [kayttaja _tiedot]
        (hae-tarjoushinnat db kayttaja)))
    (julkaise-palvelu http-palvelin :paivita-tarjoushinnat
      (fn [kayttaja tiedot]
        (paivita-tarjoushinnat db kayttaja tiedot)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-tarjoushinnat
      :paivita-tarjoushinnat)
    this))
