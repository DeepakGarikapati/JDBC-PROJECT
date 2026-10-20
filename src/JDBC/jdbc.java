package JDBC;

import java.sql.Connection;
import java.sql.DriverManager;

public class jdbc {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		try
		{
			Class.forName("com.mysql.cj.jdbc.Driver");
			Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/jdbc_first", "root", "Deepak@12345");
			System.out.println("Connected...");
			con.close();
 
 
		}
		catch(Exception e)
		{
			System.out.println(e);
		}

	}

}
