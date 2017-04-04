(ns harja.palvelin.integraatiot.sahke.sanomat.urakkasanoma)


(defn muodosta [urakka]
  [:sampo2harja
   {:xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
    :xsi:nonamespaceschemalocation "SampToharja.xsd"}
   [:project#TESTIURAKKA
    {:financialdepartmenthash "KP981303"
     :schedule_finish "2020-12-31T17:00:00.0"
     :name "Testiurakka"
     :vv_alueurakkanro "TH-123"
     :resourceid "TESTIHENKILO"
     :schedule_start "2013-01-01T08:00:00.0"
     :message_id "UrakkaMessageId"
     :programid "TESTIHANKE"
     :vv_transferred_harja "2006-08-19T20:27:14+03:00"}
    [:documentlinks]]])
