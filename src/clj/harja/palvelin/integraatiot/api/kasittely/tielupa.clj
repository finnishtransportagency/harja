(ns harja.palvelin.integraatiot.api.kasittely.tielupa)

(def testidata
  {:otsikko
   {:lahettaja
    {:jarjestelma "Urakoitsijan järjestelmä",
     :organisaatio {:nimi "Urakoitsija", :ytunnus "1234567-8"}},
    :viestintunniste {:id 123},
    :lahetysaika "2016-01-30T12:00:00+02:00"},
   :tielupa
   {:perustiedot
    {:kohteen-postinumero "90900",
     :kohteen-postitoimipaikka "Kiiminki",
     :voimassaolon-alkupvm "2020-09-22T12:00:00+02:00",
     :kohteen-lahiosoite "Tie 123",
     :kunta "Kiiminki",
     :paatoksen-diaarinumero "123456789",
     :saapumispvm "2017-09-22T12:00:00+02:00",
     :otsikko "Lupa tehdä töitä",
     :ely "Pohjois-Pohjanmaa",
     :katselmus-url "https://tilu.fi/1234",
     :tunniste {:id 1234},
     :tien-nimi "Kuusamontie",
     :myontamispvm "2018-09-22T12:00:00+02:00",
     :alueurakka "Oulun alueurakka",
     :tyyppi "työlupa",
     :voimassaolon-loppupvm "2020-09-22T12:00:00+02:00"},
    :sijainnit
    [{:sijainti
      {:numero 20,
       :aet 2631,
       :aosa 6,
       :ajorata 0,
       :kaista 1,
       :puoli 0,
       :sijoitus "oikea"}}],
    :hakija
    {:nimi "Henna Hakija",
     :postiosoite "Liitintie 1",
     :postinumero "90900",
     :puhelinnumero "987-7889087",
     :sahkopostiosoite "henna.hakija@example.com",
     :tyyppi "kotitalous"},
    :urakoitsija
    {:nimi "Puulaaki Oy",
     :yhteyshenkilo "Yrjänä Yhteyshenkilo",
     :puhelinnumero "987-7889087",
     :sahkopostiosoite "yrjana.yhteyshenkilo@example.com"},
    :liikenteenohjauksesta-vastaava
    {:nimi "Liikenneohjaus Oy",
     :yhteyshenkilo "Lilli Liikenteenohjaaja",
     :puhelinnumero "987-7889087",
     :sahkopostiosoite "lilli.liikenteenohjaaja@example.com"},
    :tienpitoviranomainen
    {:yhteyshenkilo "Teijo Tienpitäjä",
     :puhelinnumero "987-7889087",
     :sahkopostiosoite "teijo.tienpitaja@example.com"},
    :valmistumisilmoitus
    {:vaaditaan true,
     :palautettu true,
     :valmistumisilmoitus "Työt valmistuneet 22.9.2017"},
    :tyolupa
    {:ilmoitus-tieliikennekeskukseen true,
     :los-puuttuu false,
     :tyon-sisalto "Töitä",
     :ohjeet-tyon-suorittamiseen "",
     :viimeistely-oltava "2017-10-22T12:00:00+02:00",
     :los-lisatiedot "",
     :tieliikennekusksen-sahkopostiosoite "",
     :tilapainen-nopeusrajoitus true,
     :tyon-saa-aloittaa "2017-09-22T12:00:00+02:00"}}})

(defn tallennettava-tielupa [data]

  )