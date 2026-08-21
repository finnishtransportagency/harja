(ns harja.palvelin.palvelut.yllapitokohteet.kustannukset-palvelu
  "Ylläpidon kustannukset näkymän palvelut"
  (:require [clojure.string :as str]
            [com.stuartsierra.component :as component]

            [harja.pvm :as pvm]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.urakat :as urakka-kyselyt]
            [harja.palvelin.palvelut.laadunseuranta :as laadunseuranta]
            [harja.kyselyt.yllapito-kustannukset-kyselyt :as q]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))


(defn hae-paikkaus-kustannukset [db kayttaja {:keys [urakka-id aikavali vuosi] :as _tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-toteumat kayttaja urakka-id)
  (let [urakan-tiedot (first (urakka-kyselyt/hae-urakka db {:id urakka-id}))
        alkupvm (:alkupvm urakan-tiedot)
        loppupvm (:loppupvm urakan-tiedot)
        ;; Haetaan urakan kokonaiskustannukset
        kokonaisparametrit {:alkuaika alkupvm
                            :loppuaika loppupvm
                            :alkuvuosi (pvm/vuosi alkupvm)
                            :loppuvuosi (pvm/vuosi loppupvm) ;; Päällystysurakat päättyy 31.12. joten ei tarvitse vähentää 1 vuotta
                            :vuosi nil
                            :urakka-id urakka-id}

        sanktiot-ja-bonukset (laadunseuranta/hae-urakan-sanktiot-ja-bonukset db kayttaja {:hae-sanktiot? true
                                                                                          :hae-bonukset? true
                                                                                          :urakka-id urakka-id
                                                                                          :alku alkupvm
                                                                                          :loppu loppupvm})

        kokonaiskustannus-vastaus (q/hae-paikkaus-kustannukset db kokonaisparametrit)
        yht (reduce + (map (fn [rivi]
                             (or (:kokonaiskustannus rivi) 0))
                        kokonaiskustannus-vastaus))

        ;; Laske sakot ja bonukset mukaan urakan kokonaiskustannuksiin
        yht (+ yht (reduce + (map #(or (:summa %) 0) sanktiot-ja-bonukset)))

        parametrit {:alkuaika (when
                                (and
                                  (some? aikavali)
                                  (first aikavali))
                                (konversio/sql-date (first aikavali)))
                    :loppuaika (when
                                 (and
                                   (some? aikavali)
                                   (second aikavali))
                                 (konversio/sql-date (second aikavali)))
                    :vuosi vuosi
                    :alkuvuosi nil
                    :loppuvuosi nil
                    :urakka-id urakka-id}
        vastaus (q/hae-paikkaus-kustannukset db parametrit)]
    {:kustannukset vastaus
     :urakka-ajan-kustannukset-yhteensa yht}))


(defn tallenna-yllapito-kustannus
  [db kayttaja {:keys [urakka-id selite kustannustyyppi summa vuosi id poistettu?]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-toteumat kayttaja urakka-id)
  (let [paivita? (some? id)
        payload {:urakka-id urakka-id
                 ;; Jos selitettä ei kirjaa, tämä menee kantaan tyhjänä stringinä ""
                 ;; Siksi tämä iffittely, tämä asettaa kolumnin NULLiksi jos selite on tyhjä.
                 :selite (if (str/blank? selite) nil selite)
                 :kustannustyyppi kustannustyyppi
                 :summa summa
                 :vuosi vuosi
                 :luoja (:id kayttaja)
                 :id id
                 :poistettu (or poistettu? false)}]
    (if paivita?
      (q/paivita-yllapito-kustannus! db payload)
      (q/tallenna-yllapito-kustannus! db payload))))


(defn paivita-yllapito-kustannukset
  [db kayttaja {:keys [urakka-id vuosi muokatut]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-toteumat kayttaja urakka-id)

  (doseq [rivi muokatut]
    (let [{:keys [selite kustannustyyppi kokonaiskustannus id poistettu]} rivi
          paivita? (some? id)
          payload {:urakka-id urakka-id
                   :selite (if (str/blank? selite) nil selite)
                   :kustannustyyppi kustannustyyppi
                   :vuosi vuosi
                   :summa kokonaiskustannus
                   :id id
                   :poistettu poistettu
                   :luoja (:id kayttaja)}]
      (if paivita?
        (q/paivita-yllapito-kustannus! db payload)
        (q/tallenna-yllapito-kustannus! db payload))))
  ;; Palauta jotain jotta frontti on tyytyväinen
  true)


(defn hae-kustannusten-selitteet [db kayttaja {:keys [urakka-id] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-toteumat kayttaja urakka-id)
  (q/hae-kustannusten-selitteet db tiedot))


(defrecord Kustannukset []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    ;; Haut
    (julkaise-palvelu http-palvelin :hae-kustannusten-selitteet (fn [user tiedot] (hae-kustannusten-selitteet db user tiedot)))
    (julkaise-palvelu http-palvelin :hae-paikkaus-kustannukset (fn [user tiedot] (hae-paikkaus-kustannukset db user tiedot)))
    ;; Tallennus
    (julkaise-palvelu http-palvelin :tallenna-yllapito-kustannus (fn [user tiedot] (tallenna-yllapito-kustannus db user tiedot)))
    (julkaise-palvelu http-palvelin :paivita-yllapito-kustannukset (fn [user tiedot] (paivita-yllapito-kustannukset db user tiedot)))
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-kustannusten-selitteet
      :hae-paikkaus-kustannukset
      :tallenna-yllapito-kustannus
      :paivita-yllapito-kustannukset)
    this))
