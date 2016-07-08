(ns harja.palvelin.integraatiot.api.yllapitokohteet
  "Ylläpitokohteiden hallinta"
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.sanomat.yllapitokohdesanomat :as yllapitokohdesanomat]
            [harja.domain.yllapitokohteet :as yllapitokohteet]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
            [harja.kyselyt.tieverkko :as q-tieverkko]
            [harja.kyselyt.konversio :as konv]

            [harja.palvelin.integraatiot.api.tyokalut.palvelut :as palvelut]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(def testi-paallystysilmoitus
  {:otsikko
   {:lahettaja
    {:jarjestelma "Urakoitsijan järjestelmä",
     :organisaatio {:nimi "Urakoitsija", :ytunnus "1234567-8"}},
    :viestintunniste {:id 123},
    :lahetysaika "2016-01-30T12:00:00Z"},
   :paallystysilmoitus
   {:yllapitokohde
    {:sijainti {:aosa 810, :aet 137, :losa 814, :let 1912},
     :alikohteet
     [{:alikohde
       {:leveys 1.2,
        :kokonaismassamaara 12.3,
        :sijainti {:aosa 810, :aet 137, :losa 812, :let 1},
        :kivi-ja-sideaineet
        [{:kivi-ja-sideaine
          {:esiintyma "testi",
           :km-arvo "testi",
           :muotoarvo "testi",
           :sideainetyyppi "1",
           :pitoisuus 1.2,
           :lisa-aineet "lisäaineet"}}],
        :tunnus "A",
        :pinta-ala 2.2,
        :massamenekki 22,
        :kuulamylly "N14",
        :nimi "1. testialikohde",
        :raekoko 12,
        :tyomenetelma "Uraremix",
        :rc-prosentti 54,
        :paallystetyyppi "avoin asfaltti"}}]}}}
  )


(defn kirjaa-paallystysilmoitus [db kayttaja {:keys [urakka-id kohde-id]} data]
  (let [urakka-id (Integer/parseInt urakka-id)
        kohde-id (Integer/parseInt kohde-id)]
    (log/debug (format "Kirjataan urakan (id: %s) kohteelle (id: %s) päällystysilmoitus" urakka-id kohde-id))
    (clojure.pprint/pprint data)

    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (validointi/tarkista-urakan-kohde db urakka-id kohde-id)
    (let [kohteen-sijainti (get-in data [:paallystysilmoitus :yllapitokohde :sijainti])
          alikohteet (mapv :alikohde (get-in data [:paallystysilmoitus :yllapitokohde :alikohteet]))
          kohde (first (q-yllapitokohteet/hae-yllapitokohde db {:id kohde-id}))
          kohteen-tienumero (:tr-numero kohde)
          alustatoimenpiteet (mapv :alustatoimenpide (get-in data [:paallystysilmoitus :alustatoimenpiteet]))]
      (validointi/tarkista-paallystysilmoitus db kohde-id kohteen-tienumero kohteen-sijainti alikohteet alustatoimenpiteet)

      ;; hae kohteen tiedot ja tarkista sijainnit vielä kantaa vasten
      ;; tuhoa kohteen-alikohteet
      ;; tallenna uudet alikohteet
      ;; tallenna päällystysilmoituksen tiedot
      )

    (tee-kirjausvastauksen-body {:ilmoitukset (str "Päällystysilmoitus kirjattu onnistuneesti.")
                                 ;; todo: palauta uusi id
                                 :id nil})))

(defn hae-yllapitokohteet [db parametit kayttaja]
  (let [urakka-id (Integer/parseInt (:id parametit))]
    (log/debug (format "Haetaan urakan (id: %s) ylläpitokohteet käyttäjälle: %s (id: %s)."
                       urakka-id
                       (:kayttajanimi kayttaja)
                       (:id kayttaja)))
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (let [yllapitokohteet (into []
                                (map konv/alaviiva->rakenne)
                                (q-yllapitokohteet/hae-urakan-yllapitokohteet-alikohteineen db {:urakka urakka-id}))
          yllapitokohteet (konv/sarakkeet-vektoriin
                            yllapitokohteet
                            {:kohdeosa :alikohteet}
                            :id)]
      (yllapitokohdesanomat/rakenna-kohteet yllapitokohteet))))

(def palvelut
  [{:palvelu :hae-yllapitokohteet
    :polku "/api/urakat/:id/yllapitokohteet"
    :tyyppi :GET
    :vastaus-skeema json-skeemat/urakan-yllapitokohteiden-haku-vastaus
    :kasittely-fn (fn [parametit _ kayttaja db] (hae-yllapitokohteet db parametit kayttaja))}
   {:palvelu :kirjaa-paallystysilmoitus
    :polku "/api/urakat/:urakka-id/yllapitokohteet/:kohde-id/paallystysilmoitus"
    :tyyppi :POST
    :kutsu-skeema json-skeemat/paallystysilmoituksen-kirjaus
    :vastaus-skeema json-skeemat/kirjausvastaus
    :kasittely-fn (fn [parametrit data kayttaja db] (kirjaa-paallystysilmoitus db kayttaja parametrit data))}])

(defrecord Yllapitokohteet []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (palvelut/julkaise http db integraatioloki palvelut)
    this)
  (stop [{http :http-palvelin :as this}]
    (palvelut/poista http palvelut)
    this))
