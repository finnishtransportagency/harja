(ns harja.palvelin.integraatiot.api.sanomat.paivystajatiedot
  (:require [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :as kutsukasittely]))

(defn muodosta-paivystykset [paivystykset]
  (mapv (fn [{:keys [id yhteyshenkilo_id vastuuhenkilo varahenkilo alku loppu etunimi
                     sukunimi sahkoposti tyopuhelin matkapuhelin]}]
          {:paivystys
           {:id id
            :paivystaja {:id yhteyshenkilo_id
                         :etunimi etunimi
                         :sukunimi sukunimi
                         :email sahkoposti
                         :tyopuhelin tyopuhelin
                         :matkapuhelin matkapuhelin}
            :alku alku
            :loppu loppu
            :vastuuhenkilo vastuuhenkilo
            :varahenkilo varahenkilo}})
        paivystykset))

(defn muodosta-hakuvastaussanoma [urakkaryhmat paivystajatiedot]
  {:paivystajatiedot
   (mapv
     (fn [urakka-id]
       (let [urakan-paivystykset (filter
                                   #(= (:urakka_id %) urakka-id)
                                   paivystajatiedot)
             {:keys [urakka_id urakka_nimi urakka_alkupvm
                     urakka_loppupvm urakka_tyyppi]} (first urakan-paivystykset)
             {:keys [urakoitsija_nimi urakoitsija_ytunnus]} (first urakan-paivystykset)]
         {:urakka {:tiedot {:id urakka_id
                            :nimi urakka_nimi
                            :urakoitsija {:ytunnus urakoitsija_ytunnus
                                          :nimi urakoitsija_nimi}
                            :vaylamuoto "tie"
                            :tyyppi (if (= "teiden-hoito" urakka_tyyppi) "hoito" urakka_tyyppi)
                            :alkupvm urakka_alkupvm
                            :loppupvm urakka_loppupvm}
                   :paivystykset (muodosta-paivystykset urakan-paivystykset)}}))
     urakkaryhmat)})

(defn muodosta-vastaus-paivystajatietojen-haulle [paivystajatiedot]
  (if (empty? paivystajatiedot)
    {:paivystajatiedot []}
    (let [urakkaryhmat (keys (group-by :urakka_id paivystajatiedot))]
      (muodosta-hakuvastaussanoma urakkaryhmat paivystajatiedot))))

(defn tee-onnistunut-kirjaus-vastaus []
  (kutsukasittely/tee-kirjausvastauksen-body {:ilmoitukset "Päivystäjätiedot kirjattu onnistuneesti"}))

(defn tee-onnistunut-poisto-vastaus []
  (kutsukasittely/tee-kirjausvastauksen-body {:ilmoitukset "Päivystykset poistettu onnistuneesti"}))
