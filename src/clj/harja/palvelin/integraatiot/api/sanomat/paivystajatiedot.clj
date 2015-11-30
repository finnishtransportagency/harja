(ns harja.palvelin.integraatiot.api.sanomat.paivystajatiedot
  (:require [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]))

(defn muodosta-vastaus-paivystajatietojen-haulle [paivystajatiedot]
  (if (empty? paivystajatiedot)
    (throw+ {:type    virheet/+paivystajia-ei-loydy+
             :virheet [{:koodi  virheet/+paivystajia-ei-loydy+
                        :viesti "Päivystäjiä ei löydy."}]})
    (let [urakkaryhmat (keys (group-by :urakka_id paivystajatiedot))
          vastaus {:urakat (mapv
                             (fn [urakka-id]
                               (let [urakan-paivystykset (filter
                                                           #(= (:urakka_id %) urakka-id)
                                                           paivystajatiedot)
                                     {:keys [urakka_id urakka_nimi urakka_alkupvm
                                             urakka_loppupvm urakka_tyyppi]} (first urakan-paivystykset)
                                     {:keys [organisaatio_nimi organisaatio_ytunnus]} (first urakan-paivystykset)]
                                 {:urakka {:tiedot       {:id          urakka_id
                                                          :nimi        urakka_nimi
                                                          :urakoitsija {:ytunnus organisaatio_ytunnus
                                                                        :nimi    organisaatio_nimi}
                                                          :vaylamuoto  "tie"
                                                          :tyyppi      urakka_tyyppi
                                                          :alkupvm     urakka_alkupvm
                                                          :loppupvm    urakka_loppupvm}
                                           :paivystykset (mapv (fn [{:keys [id vastuuhenkilo varahenkilo alku loppu etunimi
                                                                            sukunimi sahkoposti tyopuhelin matkapuhelin]}]
                                                                 {:paivystys {:paivystaja    {:id           id
                                                                                              :etunimi      etunimi
                                                                                              :sukunimi     sukunimi
                                                                                              :email        sahkoposti
                                                                                              :tyopuhelin   tyopuhelin
                                                                                              :matkapuhelin matkapuhelin}
                                                                              :alku          alku
                                                                              :loppu         loppu
                                                                              :vastuuhenkilo vastuuhenkilo
                                                                              :varahenkilo   varahenkilo}})
                                                               urakan-paivystykset)}}))
                             urakkaryhmat)}]
      vastaus)))


(defn tee-onnistunut-kirjaus-vastaus []
  (let [vastauksen-data {:ilmoitukset "Päivystäjätiedot kirjattu onnistuneesti"}]
    vastauksen-data))