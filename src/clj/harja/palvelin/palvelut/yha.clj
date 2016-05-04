(ns harja.palvelin.palvelut.yha
  "Paikallisen kannan YHA-tietojenkäsittelyn logiikka"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.yha :as yha-q]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha]
            [harja.kyselyt.konversio :as konversio]
            [harja.domain.oikeudet :as oikeudet]
            [cheshire.core :as cheshire]))

(defn- lisaa-urakalle-yha-tiedot [db user urakka-id {:keys [yhatunnus yhaid yhanimi elyt vuodet] :as yha-tiedot}]
  (log/debug "Lisätään YHA-tiedot urakalle " urakka-id)
  (yha-q/lisaa-urakalle-yha-tiedot<! db {:urakka urakka-id
                                         :yhatunnus yhatunnus
                                         :yhaid yhaid
                                         :yhanimi yhanimi
                                         :elyt (konv/seq->array elyt)
                                         :vuodet (konv/seq->array (map str vuodet))
                                         :kayttaja (:id user)}))

(defn- poista-urakan-yha-tiedot [db urakka-id]
  (log/debug "Poistetaan urakan " urakka-id " vanhat YHA-tiedot")
  (yha-q/poista-urakan-yha-tiedot! db {:urakka urakka-id}))

(defn- poista-urakan-yllapitokohteet [db urakka-id]
  (log/debug "Poistetaan urakan " urakka-id " ylläpitokohteet")
  (yha-q/poista-urakan-yllapitokohdeosat! db {:urakka urakka-id})
  (yha-q/poista-urakan-yllapitokohteet! db {:urakka urakka-id}))

(defn- hae-urakan-yha-tiedot [db urakka-id]
  (first (into []
               (comp
                 (map #(konv/array->vec % :vuodet))
                 (map #(konv/array->vec % :elyt)))
               (yha-q/hae-urakan-yhatiedot db {:urakka urakka-id}))))

(defn- sido-yha-urakka-harja-urakkaan [db user {:keys [harja-urakka-id yha-tiedot]}]
  (oikeudet/on-muu-oikeus? "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet harja-urakka-id user)
  (log/debug "Käsitellään pyyntö lisätä Harja-urakalle " harja-urakka-id " yha-tiedot: " yha-tiedot)
  (jdbc/with-db-transaction [db db]
    (poista-urakan-yha-tiedot db harja-urakka-id)
    (poista-urakan-yllapitokohteet db harja-urakka-id)
    (lisaa-urakalle-yha-tiedot db user harja-urakka-id yha-tiedot)
    (log/debug "YHA-tiedot sidottu. Palautetaan urakan YHA-tiedot")
    (hae-urakan-yha-tiedot db harja-urakka-id)))


(defn- hae-urakat-yhasta [db yha user {:keys [yhatunniste sampotunniste vuosi harja-urakka-id]}]
  (oikeudet/on-muu-oikeus? "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet harja-urakka-id user)
  (let [urakat (yha/hae-urakat yha yhatunniste sampotunniste vuosi)
        yhaidt (mapv :yhaid urakat)
        sidontatiedot (when (not-empty yhaidt) (yha-q/hae-urakoiden-sidontatiedot db {:yhaidt yhaidt}))
        urakat (mapv second
                     (merge-with merge
                                 (into {} (map (juxt :yhaid identity) urakat))
                                 (into {} (map (juxt :yhaid identity) sidontatiedot))))]
    urakat))

(defn- suodata-olemassaolevat-kohteet [db urakka-id kohteet]
  (let [yha-idt (into #{} (map :yhaid (yha-q/hae-urakan-kohteiden-yha-idt db {:urakkaid urakka-id})))
        _ (log/debug "Urakan " urakka-id " kohteiden yha:idt: " (pr-str yha-idt))]
    (filter #(not (yha-idt (:yhaid %))) kohteet)))

(defn- hae-yha-kohteet [db yha user {:keys [urakka-id] :as tiedot}]
  (oikeudet/on-muu-oikeus? "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet urakka-id user)
  (log/debug "Haetaan kohteet yhasta")
  (let [yha-kohteet (yha/hae-kohteet yha urakka-id)
        _ (log/debug "Kohteita löytyi " (count yha-kohteet) " kpl.")
        uudet-kohteet (suodata-olemassaolevat-kohteet db urakka-id yha-kohteet)
        _ (log/debug "Uusia kohteita oli " (count uudet-kohteet) " kpl.")]
    uudet-kohteet))

(defn- merkitse-urakan-kohdeluettelo-paivitetyksi [db harja-urakka-id]
  (log/debug "Merkitään urakan " harja-urakka-id " kohdeluettelo päivitetyksi")
  (yha-q/merkitse-urakan-yllapitokohteet-paivitetyksi<! db {:urakka harja-urakka-id}))

(defn- luo-esitaytetty-paallystysilmoitus [db user kohde kohdeosat]
  (log/debug "Tehdään kohdeosista esitäytetty päällystysilmoitus")
  (let [ilmoitustiedot {:osoitteet
                        (mapv
                          (fn [{:keys [tierekisteriosoitevali paallystystoimenpide] :as kohdeosa}]
                            {:tie (:tienumero tierekisteriosoitevali)
                             :aosa (:aosa tierekisteriosoitevali)
                             :aet (:aet tierekisteriosoitevali)
                             :losa (:losa tierekisteriosoitevali)
                             :let (:let tierekisteriosoitevali)
                             :rc% (:rc-prosentti paallystystoimenpide)
                             ; FIXME Onko tämä nyt "massa" vai "massamäärä"? Onko toinen turha?
                             :massa (:kokonaismassa paallystystoimenpide)
                             :raekoko (:raekoko paallystystoimenpide)
                             :kuulamylly (:kuulamylly paallystystoimenpide)
                             :tyomenetelma (:paallystetyomenetelma paallystystoimenpide)
                             :paallystetyyppi (:uusi-paallyste paallystystoimenpide)})
                          kohdeosat)}
        ilmoitustiedot-json (cheshire/encode ilmoitustiedot)]
    (yha-q/luo-paallystysilmoitus<! db {:paallystyskohde (:id kohde)
                                        :ilmoitustiedot ilmoitustiedot-json
                                        :luoja (:id user)})
    (log/debug "Esitäytetty päällystysilmoitus tehty")))

(defn- tallenna-uudet-yha-kohteet
  "Tallentaa YHA:sta tulleet ylläpitokohteet. Olettaa, että ollaan tallentamassa vain
  uusia kohteita eli jo olemassa olevat on suodatettu joukosta pois."
  [db user {:keys [urakka-id kohteet] :as tiedot}]
  (oikeudet/on-muu-oikeus? "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet urakka-id user)
  (log/debug "Tallennetaan " (count kohteet) " yha-kohdetta")
  (jdbc/with-db-transaction [c db]
    (doseq [{:keys [tierekisteriosoitevali
                    tunnus yha-id alikohteet kohdetyyppi
                    yllapitoluokka
                    keskimaarainen_vuorokausiliikenne
                    nykyinen-paallyste] :as kohde} kohteet]
      (log/debug "Tallennetaan kohde, jonka yha-id on: " yha-id)
      (let [kohde (yha-q/luo-yllapitokohde<! c
                                             {:urakka urakka-id
                                              :tr_numero (:tienumero tierekisteriosoitevali)
                                              :tr_alkuosa (:aosa tierekisteriosoitevali)
                                              :tr_alkuetaisyys (:aet tierekisteriosoitevali)
                                              :tr_loppuosa (:losa tierekisteriosoitevali)
                                              :tr_loppuetaisyys (:let tierekisteriosoitevali)
                                              :yhatunnus tunnus
                                              :yhaid yha-id
                                              :tyyppi (name kohdetyyppi)
                                              :yllapitoluokka yllapitoluokka
                                              :keskimaarainen_vuorokausiliikenne keskimaarainen_vuorokausiliikenne
                                              :nykyinen_paallyste nykyinen-paallyste})]
        (doseq [{:keys [sijainti tierekisteriosoitevali yha-id] :as alikohde} alikohteet]
          (log/debug "Tallennetaan kohteen osa, jonka yha-id on " yha-id)
          (yha-q/luo-yllapitokohdeosa<! c
                                        {:yllapitokohde (:id kohde)
                                         :nimi tunnus
                                         :sijainti sijainti
                                         :tr_numero (:tienumero tierekisteriosoitevali)
                                         :tr_alkuosa (:aosa tierekisteriosoitevali)
                                         :tr_alkuetaisyys (:aet tierekisteriosoitevali)
                                         :tr_loppuosa (:losa tierekisteriosoitevali)
                                         :tr_loppuetaisyys (:let tierekisteriosoitevali)
                                         :yhaid yha-id}))
        (when (= kohdetyyppi :paallystys)
          (luo-esitaytetty-paallystysilmoitus c user kohde alikohteet))))
    (merkitse-urakan-kohdeluettelo-paivitetyksi c urakka-id)
    (log/debug "YHA-kohteet tallennettu")
    (hae-urakan-yha-tiedot c urakka-id)))

(defrecord Yha []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)
          yha (:yha-integraatio this)]
      (julkaise-palvelu http :sido-yha-urakka-harja-urakkaan
                        (fn [user tiedot]
                          (sido-yha-urakka-harja-urakkaan db user tiedot)))
      (julkaise-palvelu http :hae-urakat-yhasta
                        (fn [user tiedot]
                          (hae-urakat-yhasta db yha user tiedot)))
      (julkaise-palvelu http :hae-yha-kohteet
                        (fn [user tiedot]
                          (hae-yha-kohteet db yha user tiedot)))
      (julkaise-palvelu http :tallenna-uudet-yha-kohteet
                        (fn [user tiedot]
                          (tallenna-uudet-yha-kohteet db user tiedot)))))
  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :sido-yha-urakka-harja-urakkaan)
    this))
