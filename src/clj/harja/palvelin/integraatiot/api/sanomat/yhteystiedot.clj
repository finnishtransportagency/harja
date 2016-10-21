(ns harja.palvelin.integraatiot.api.sanomat.yhteystiedot)

(def fim-roolit
  #{"Urakan vastuuhenkilö"
    "ELY urakanvalvoja"
    "ELY laadunvalvoja"
    "ELY turvallisuusvastaava"
    "ELY paakayttaja"
    "Tilaajan asiantuntija"
    "Tilaajan kayttaja"
    "Tilaajan urakanvalvoja"
    "Tilaajan laadunvalvoja"
    "Tilaajan turvallisuusvastaava"})

(def harja-roolit
  #{"Sampo yhteyshenkilö"
    "Kunnossapitopäällikkö"
    "Sillanvalvoja"
    "Kelikeskus"
    "Tieliikennekeskus"})

(defn tee-yhteyshenkilo [rooli etunimi sukunimi puhelin sahkoposti organisaatio]
  {:yhteyshenkilo
   {:rooli rooli
    :nimi (str etunimi " " sukunimi)
    :puhelinnumero puhelin
    :email sahkoposti
    :organisaatio organisaatio}})

(defn yhteyshenkilot-fimissa [yhteyshenkilot]
  (let [yhteyshenkilot (filter (fn [k] some #(contains? fim-roolit %) (:roolit k)) yhteyshenkilot)]
    (apply concat
           (map
             (fn [rooli]
               (map
                 (fn [{:keys [etunimi sukunimi puhelin sahkoposti organisaatio]}]
                   (tee-yhteyshenkilo rooli etunimi sukunimi puhelin sahkoposti organisaatio))
                 (filter (fn [y] (some #(= % rooli) (:roolit y))) yhteyshenkilot)))
             fim-roolit))))

(defn yhteyshenkilot-harjassa [yhteyshenkilot]
  (let [yhteyshenkilot (filter #(contains? harja-roolit (:rooli %)) yhteyshenkilot)]
    (map
      (fn [{:keys [rooli etunimi sukunimi matkapuhelin tyopuhelin sahkoposti organisaatio_nimi]}]
        (let [puhelin (or matkapuhelin tyopuhelin)]
          (tee-yhteyshenkilo rooli etunimi sukunimi puhelin sahkoposti organisaatio_nimi)))
      yhteyshenkilot)))

(defn yhteyshenkilot [fim-yhteyshenkilot harja-yhteyshenkilot]
  (vec (concat (yhteyshenkilot-fimissa fim-yhteyshenkilot)
               (yhteyshenkilot-harjassa harja-yhteyshenkilot))))

(defn urakan-yhteystiedot [{:keys [urakkanro elynumero
                                   elynimi
                                   nimi
                                   sampoid
                                   alkupvm
                                   loppupvm
                                   urakoitsija-ytunnus
                                   urakoitsija-katuosoite
                                   urakoitsija-postinumero
                                   urakoitsija-nimi]}
                           fim-yhteyshenkilot
                           harja-yhteyshenkilot]

  (let [yhteyshenkilot (yhteyshenkilot fim-yhteyshenkilot harja-yhteyshenkilot)]
    {:urakka {
              :alueurakkanro urakkanro
              :elynro elynumero
              :elynimi elynimi
              :nimi nimi
              :sampoid sampoid
              :alkupvm alkupvm
              :loppupvm loppupvm
              :urakoitsija
              {:nimi urakoitsija-nimi
               :ytunnus urakoitsija-ytunnus
               :katuosoite urakoitsija-katuosoite
               :postinumero urakoitsija-postinumero}
              :yhteyshenkilot yhteyshenkilot}}))
